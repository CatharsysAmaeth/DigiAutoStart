package pt.catharsys.digiautostart

import android.content.Context
import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.iface.instruction.NarrowLiteralInstruction
import org.jf.dexlib2.iface.instruction.OffsetInstruction
import org.jf.dexlib2.iface.instruction.ReferenceInstruction
import org.jf.dexlib2.iface.instruction.SwitchPayload
import org.jf.dexlib2.iface.instruction.WideLiteralInstruction
import org.jf.dexlib2.iface.reference.FieldReference
import org.jf.dexlib2.iface.reference.MethodReference
import org.jf.dexlib2.iface.reference.StringReference
import org.jf.dexlib2.iface.reference.TypeReference
import org.jf.dexlib2.iface.value.IntEncodedValue
import java.io.File

/**
 * Debug-only, read-only summary of the ZTE TvSettings AIDL contract.
 * Never binds to or invokes an OEM service.
 */
internal object StaticDexProbe {
    private const val MAX_OUTPUT_LINES = 240
    private val classTargets = listOf("TvSettingsService", "SettingsInterfaceImp", "IHgagentService")
    private val securityNames = Regex(
        "(?i)(getCallingUid|getCallingPid|getCallingUserHandle|getCallingWorkSourceUid|myUid|" +
            "checkCallingPermission|checkCallingOrSelfPermission|enforceCallingPermission|" +
            "enforceCallingOrSelfPermission|checkPermission|enforcePermission|checkSignatures|" +
            "getPackageName|isSystemApp|hasSystemFeature|transact|onBind|onTransact|asInterface|" +
            "asBinder|getInterfaceDescriptor|getTransactionName|getDefaultTransactionName|" +
            "attachInterface|queryLocalInterface|enforceInterface|enforceNoDataAvail|writeToParcel|" +
            "readException|writeInterfaceToken)"
    )
    private val securityStrings = Regex(
        "(?i)(permission|calling.?uid|calling.?pid|package.?name|not.?allowed|denied|security|caller|descriptor|transaction|gateway|wifi)"
    )

    @Suppress("UNUSED_PARAMETER")
    fun inspect(bytes: ByteArray, entryName: String, output: MutableList<String>, cacheDir: File, context: Context) {
        val startSize = output.size
        val temp = File.createTempFile("dada-tvsettings-", ".dex", cacheDir)
        try {
            temp.writeBytes(bytes)
            val dex = DexFileFactory.loadDexFile(temp, Opcodes.getDefault())
            val selected = dex.classes
                .filter { clazz ->
                    classTargets.any { clazz.type.contains(it, ignoreCase = true) } &&
                        !clazz.type.contains("ExternalSyntheticLambda", ignoreCase = true)
                }
                .sortedWith(compareBy({ classPriority(it.type) }, { it.type }))

            output += "dexContract=$entryName;selectedClasses=${selected.size};mode=static-read-only"
            for (clazz in selected) {
                if (output.size - startSize >= MAX_OUTPUT_LINES) break
                val type = clazz.type
                val isService = type.endsWith("/TvSettingsService;")
                val isSettingsImpl = type.contains("SettingsInterfaceImp", true)
                val isAidl = type.contains("IHgagentService", true)
                val isStub = type.contains("\$Stub", true)
                val isProxy = type.contains("\$Proxy", true)

                output += "dexContractClass=$type;super=${clazz.superclass ?: "none"};interfaces=${clazz.interfaces.joinToString(",")};flags=${clazz.accessFlags}"
                clazz.fields
                    .filter { isService || isAidl || it.name.contains("DESCRIPTOR", true) || it.name.contains("TRANSACTION", true) }
                    .forEach { field ->
                        if (output.size - startSize < MAX_OUTPUT_LINES) {
                            val value = when (val encoded = field.initialValue) {
                                is IntEncodedValue -> encoded.value.toString()
                                null -> "unset"
                                else -> encoded.toString()
                            }
                            output += "dexContractField=${field.name}:${field.type}=$value"
                        }
                    }

                val methods = clazz.methods.sortedWith(compareBy(
                    { methodPriority(it.name, isStub, isProxy) },
                    { it.name },
                    { it.parameterTypes.joinToString(",") }
                ))
                for (method in methods) {
                    if (output.size - startSize >= MAX_OUTPUT_LINES) break
                    val signature = method.parameterTypes.joinToString(",") { it.toString() }
                    val methodId = "$type->${method.name}($signature)${method.returnType}"
                    val impl = method.implementation
                    output += "dexContractMethod=$methodId;implemented=${impl != null};instructions=${impl?.instructions?.count() ?: 0}"
                    if (impl == null) continue

                    val instructions = impl.instructions.toList()
                    val detailed = isService || isStub || isProxy
                    var emitted = 0
                    val maxDetails = when {
                        method.name == "onTransact" -> 64
                        isProxy -> 36
                        isService -> 56
                        else -> 12
                    }
                    for ((index, instruction) in instructions.withIndex()) {
                        if (emitted >= maxDetails || output.size - startSize >= MAX_OUTPUT_LINES) break
                        val ref = (instruction as? ReferenceInstruction)?.reference
                        val refText = when (ref) {
                            is StringReference -> "str=${ref.string.take(100)}"
                            is MethodReference -> "call=${ref.definingClass}->${ref.name}(${ref.parameterTypes.joinToString("")})${ref.returnType}"
                            is TypeReference -> "type=${ref.type}"
                            is FieldReference -> "field=${ref.definingClass}->${ref.name}:${ref.type}"
                            else -> ""
                        }
                        val securityRef = ref is MethodReference && securityNames.containsMatchIn(ref.name)
                        val securityText = ref is StringReference && securityStrings.containsMatchIn(ref.string)
                        val transactionData = (isStub || isProxy) && (
                            instruction is NarrowLiteralInstruction ||
                                instruction is WideLiteralInstruction ||
                                ref is StringReference ||
                                ref is MethodReference ||
                                ref is FieldReference ||
                                instruction is SwitchPayload
                            )
                        val serviceData = isService && (
                            securityRef || securityText ||
                                (ref is MethodReference && ref.definingClass.contains("IHgagentService")) ||
                                (ref is StringReference && securityStrings.containsMatchIn(ref.string)) ||
                                method.name in setOf("onBind", "onStartCommand", "getWifiInfo")
                            )
                        val implementationSecurity = isSettingsImpl && (securityRef || securityText ||
                            (ref is MethodReference && ref.definingClass.contains("IHgagentService")))

                        if (detailed && (transactionData || serviceData || securityRef || securityText) ||
                            implementationSecurity
                        ) {
                            val operands = buildString {
                                if (instruction is NarrowLiteralInstruction) {
                                    append(" literal=${instruction.narrowLiteral}")
                                    if (instruction.narrowLiteral > 99_999_999 || instruction.narrowLiteral < -99_999_999) {
                                        append(" hex=0x${Integer.toHexString(instruction.narrowLiteral)}")
                                    }
                                }
                                if (instruction is WideLiteralInstruction) append(" wide=${instruction.wideLiteral}")
                                if (instruction is OffsetInstruction) append(" offset=${instruction.codeOffset}")
                                if (instruction is SwitchPayload) {
                                    append(" switch=")
                                    append(instruction.switchElements.joinToString(",") { "${it.key}:${it.offset}" })
                                }
                            }
                            output += "dexContractInsn=${method.name}#$index:${instruction.opcode.name};$refText$operands"
                            emitted++
                        }
                    }
                }
            }
        } catch (error: Throwable) {
            output += "dexContractError=$entryName;${error.javaClass.simpleName}:${error.message.orEmpty().take(180)}"
        } finally {
            temp.delete()
        }
    }

    private fun classPriority(type: String): Int = when {
        type.endsWith("/TvSettingsService;") -> 0
        type.contains("SettingsInterfaceImp", true) -> 1
        type.contains("IHgagentService") && type.contains("\$Stub", true) -> 2
        type.contains("IHgagentService") && type.contains("\$Proxy", true) -> 3
        type.endsWith("/IHgagentService;") -> 4
        type.contains("TvSettingsService\\$1") -> 5
        else -> 6
    }

    private fun methodPriority(name: String, isStub: Boolean, isProxy: Boolean): Int = when {
        name == "onBind" -> 0
        name == "onStartCommand" -> 1
        name == "onTransact" -> 2
        name == "asInterface" -> 3
        name == "asBinder" -> 4
        name == "getDefaultTransactionName" -> 5
        name == "getTransactionName" -> 6
        name == "getWifiInfo" -> 7
        isStub && name == "getMaxTransactionId" -> 8
        isProxy -> 9
        else -> 10
    }
}
