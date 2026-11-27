package pt.catharsys.digiautostart

import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.iface.instruction.NarrowLiteralInstruction
import org.jf.dexlib2.iface.instruction.OneRegisterInstruction
import org.jf.dexlib2.iface.instruction.TwoRegisterInstruction
import org.jf.dexlib2.iface.instruction.ThreeRegisterInstruction
import org.jf.dexlib2.iface.instruction.FiveRegisterInstruction
import org.jf.dexlib2.iface.instruction.RegisterRangeInstruction
import org.jf.dexlib2.iface.instruction.SwitchPayload
import org.jf.dexlib2.iface.instruction.OffsetInstruction
import org.jf.dexlib2.iface.instruction.ReferenceInstruction
import org.jf.dexlib2.iface.instruction.WideLiteralInstruction
import org.jf.dexlib2.iface.reference.FieldReference
import org.jf.dexlib2.iface.reference.MethodReference
import org.jf.dexlib2.iface.reference.StringReference
import org.jf.dexlib2.iface.reference.TypeReference
import org.jf.dexlib2.iface.value.IntEncodedValue
import android.content.Context
import java.io.File

/** Debug-only, read-only disassembly of selected TvSettings methods. */
internal object StaticDexProbe {
    private val classTargets = listOf(
        "/oemlink/AccessibilityServiceActivity;",
        "/oemlink/AccessibilitySettingsActivity;",
        "/TvSettingsActivity;",
        "/overlay/FlavorUtils;",
        "/library/overlay/FlavorUtils;",
        "/overlay/FeatureFactoryImpl;",
        "/overlay/FeatureFactoryImplX;",
        "/overlay/FeatureFactoryImplTwoPanel;",
        "/accessibility/AccessibilityFragment;",
        "/SettingsFragmentProvider;",
        "/accessibility/AccessibilityServiceFragment;",
        "/accessibility/AccessibilityServiceConfirmationFragment;",
        "/TvSettingsService",
        "/aidl/",
        "/ZteKeyInterceptor"
    )
    private val methodTargets = Regex(
        "(?i)(onCreate|createSettingsFragment|getAvailableFlavors|getFlavor|getFeatureFactory|" +
            "getSettingsFragmentProvider|newSettingsFragment|prepareArgs|isTwoPanel|onCreatePreferences|" +
            "onPreferenceTreeClick|onResume|setAccessibilityServiceState|finish|isStartupVerificationRequired|" +
            "onKey|intercept|dispatchKey|handleKey|bindService|onBind|onTransact|asBinder|asInterface|" +
            "getInterfaceDescriptor|getTransactionName|getDefaultTransactionName|attachInterface|" +
            "queryLocalInterface|enforceInterface|enforceNoDataAvail|writeTransactionToParcel)"
    )

    fun inspect(bytes: ByteArray, entryName: String, output: MutableList<String>, cacheDir: File, context: Context) {
        val temp = File.createTempFile("dada-tvsettings-", ".dex", cacheDir)
        try {
            temp.writeBytes(bytes)
            val dex = DexFileFactory.loadDexFile(temp, Opcodes.getDefault())
            val selected = dex.classes.filter { clazz ->
                classTargets.any { clazz.type.contains(it, ignoreCase = true) }
            }.sortedBy { clazz ->
                when {
                    clazz.type.contains("TvSettingsService") -> 0
                    clazz.type.contains("/aidl/") -> 1
                    clazz.type.contains("TvSettingsActivity") -> 2
                    clazz.type.contains("FlavorUtils") -> 3
                    clazz.type.contains("oemlink/AccessibilityServiceActivity") -> 4
                    clazz.type.contains("oemlink/AccessibilitySettingsActivity") -> 5
                    clazz.type.contains("FeatureFactoryImplX") -> 6
                    clazz.type.contains("FeatureFactoryImplTwoPanel") -> 7
                    clazz.type.contains("FeatureFactoryImpl") -> 8
                    clazz.type.contains("AccessibilityServiceFragment") -> 9
                    clazz.type.contains("ZteKeyInterceptor") -> 10
                    else -> 11
                }
            }
            output += "dexDisassembly=$entryName;selectedClasses=${selected.size}"
            val settingsResources = runCatching {
                context.packageManager.getResourcesForApplication("com.android.tv.settings")
            }.getOrNull()
            val queriedResources = linkedSetOf<String>()
            for (clazz in selected) {
                if (output.size >= 420) break
                val binderClass = clazz.type.contains("TvSettingsService", true) ||
                    clazz.type.contains("/aidl/", true) ||
                    clazz.type.contains("$" + "Stub", true) ||
                    clazz.type.contains("$" + "Proxy", true)
                output += "dexClass=${clazz.type};super=${clazz.superclass ?: "none"}"
                if (binderClass) {
                    output += "dexBinderClass=${clazz.type};interfaces=${clazz.interfaces.joinToString(",")};accessFlags=${clazz.accessFlags}"
                }
                clazz.fields.filter {
                    binderClass || it.name.contains("FLAVOR", true) || it.name.contains("MASK", true) ||
                        it.name.contains("DESCRIPTOR", true) || it.name.contains("TRANSACTION", true)
                }
                    .forEach { field ->
                        val encoded = field.initialValue
                        val value = when (encoded) {
                            is IntEncodedValue -> encoded.value.toString()
                            null -> "unset"
                            else -> encoded.toString()
                        }
                        output += "dexStaticField=${clazz.type}->${field.name}:${field.type}=$value"
                    }
                val methods = clazz.methods.filter { binderClass || methodTargets.containsMatchIn(it.name) }
                    .sortedBy { method ->
                        when (method.name) {
                            "onCreate" -> 0
                            "finish" -> 1
                            "getFlavor" -> 2
                            "createSettingsFragment" -> 3
                            "getAvailableFlavors" -> 4
                            "getFeatureFactory" -> 5
                            "getSettingsFragmentProvider" -> 6
                            "onCreatePreferences" -> 7
                            "onPreferenceTreeClick" -> 8
                            "onResume" -> 9
                            else -> 10
                        }
                    }
                for (method in methods) {
                    if (output.size >= 420) break
                    val impl = method.implementation
                    if (impl == null) {
                        if (binderClass) output += "dexBinderMethod=${clazz.type}->${method.name};params=${method.parameterTypes.joinToString(",")};return=${method.returnType};nativeOrAbstract=true"
                        continue
                    }
                    val signature = method.parameterTypes.joinToString("") { it.toString() }
                    val instructions = impl.instructions.toList()
                    output += (if (binderClass) "dexBinderMethod=" else "dexMethod=") +
                        "${clazz.type}->${method.name}($signature)${method.returnType};ins=${instructions.size}"
                    var emitted = 0
                    val fullFlavorMethod = clazz.type.contains("FlavorUtils") && method.name == "getFlavor"
                    val maxInstructions = when {
                        binderClass -> 96
                        fullFlavorMethod -> 220
                        clazz.type.contains("FlavorUtils") && method.name == "getFeatureFactory" -> 100
                        method.name in setOf(
                            "onCreate", "finish", "createSettingsFragment",
                            "getAvailableFlavors", "onCreatePreferences", "isTwoPanel"
                        ) -> 24
                        else -> 10
                    }
                    for ((index, instruction) in instructions.withIndex()) {
                        if (emitted >= maxInstructions || output.size >= 420) break
                        val ref = (instruction as? ReferenceInstruction)?.reference
                        if (ref is FieldReference && settingsResources != null) {
                            val resourceType = when {
                                ref.definingClass.endsWith("R\$integer;") -> "integer"
                                ref.definingClass.endsWith("R\$string;") -> "string"
                                ref.definingClass.endsWith("R\$bool;") -> "bool"
                                else -> null
                            }
                            if (resourceType != null) {
                                val key = "$resourceType/" + ref.name
                                if (queriedResources.add(key)) {
                                    val id = settingsResources.getIdentifier(ref.name, resourceType, "com.android.tv.settings")
                                    if (id != 0) {
                                        val value = runCatching {
                                            when (resourceType) {
                                                "integer" -> settingsResources.getInteger(id).toString()
                                                "string" -> settingsResources.getString(id).take(180)
                                                else -> settingsResources.getBoolean(id).toString()
                                            }
                                        }.getOrElse { "read-error:${it.javaClass.simpleName}" }
                                        output += "tvSettingsResource=$key;id=$id;value=$value"
                                    } else {
                                        output += "tvSettingsResource=$key;not-found"
                                    }
                                }
                            }
                        }
                        val refText = when (ref) {
                            is StringReference -> " str=${ref.string.take(160)}"
                            is MethodReference -> " method=${ref.definingClass}->${ref.name}(" +
                                ref.parameterTypes.joinToString("") { it.toString() } + ")${ref.returnType}"
                            is TypeReference -> " type=${ref.type}"
                            is FieldReference -> " field=${ref.definingClass}->${ref.name}:${ref.type}"
                            else -> ""
                        }
                        val lower = refText.lowercase()
                        val opcode = instruction.opcode.name.lowercase()
                        if (fullFlavorMethod && instruction is NarrowLiteralInstruction && settingsResources != null) {
                            val resourceId = instruction.narrowLiteral
                            val resourceName = runCatching { settingsResources.getResourceName(resourceId) }.getOrNull()
                            if (resourceName != null) {
                                val resourceType = runCatching { settingsResources.getResourceTypeName(resourceId) }.getOrNull()
                                val resourceValue = runCatching {
                                    when (resourceType) {
                                        "string" -> settingsResources.getString(resourceId).take(240)
                                        "integer" -> settingsResources.getInteger(resourceId).toString()
                                        "bool" -> settingsResources.getBoolean(resourceId).toString()
                                        else -> "(type=$resourceType)"
                                    }
                                }.getOrElse { "read-error:" + it.javaClass.simpleName }
                                output += "tvSettingsLiteralResource=id=0x" + java.lang.Integer.toHexString(resourceId) + ";name=$resourceName;value=$resourceValue"
                            }
                        }
                        val operands = buildString {
                            if (fullFlavorMethod) {
                                when (instruction) {
                                    is FiveRegisterInstruction -> append(" regs=v${instruction.registerC},v${instruction.registerD},v${instruction.registerE},v${instruction.registerF},v${instruction.registerG}")
                                    is ThreeRegisterInstruction -> append(" regs=v${instruction.registerA},v${instruction.registerB},v${instruction.registerC}")
                                    is TwoRegisterInstruction -> append(" regs=v${instruction.registerA},v${instruction.registerB}")
                                    is OneRegisterInstruction -> append(" regs=v${instruction.registerA}")
                                    is RegisterRangeInstruction -> append(" regs=v${instruction.startRegister}..v${instruction.startRegister + instruction.registerCount - 1}")
                                }
                                if (instruction is SwitchPayload) {
                                    append(" switch=")
                                    append(instruction.switchElements.joinToString(",") { "${it.key}:${it.offset}" })
                                }
                            }
                            if (instruction is NarrowLiteralInstruction) {
                                val literal = instruction.narrowLiteral
                                if (literal > 99_999_999 || literal < -99_999_999) {
                                    append(" literalHex=0x" + java.lang.Integer.toHexString(literal))
                                } else {
                                    append(" literal=$literal")
                                }
                            }
                            if (instruction is WideLiteralInstruction) append(" literal=${instruction.wideLiteral}")
                            if (instruction is OffsetInstruction) append(" offset=${instruction.codeOffset}")
                        }
                        val relevant = binderClass || fullFlavorMethod || (clazz.type.contains("FlavorUtils") && method.name == "getFeatureFactory") ||
                            ref is MethodReference || ref is FieldReference ||
                            (ref is StringReference && (
                                lower.contains("accessibility") || lower.contains("flavor") ||
                                    lower.contains("unsupported") || lower.contains("service")
                            )) ||
                            (ref is TypeReference && (
                                lower.contains("accessibility") || lower.contains("fragment") ||
                                    lower.contains("factory") || lower.contains("service")
                            )) ||
                            opcode.startsWith("const") || opcode.startsWith("if-") || opcode.startsWith("goto") ||
                            opcode.startsWith("return") || opcode.startsWith("throw")
                        if (relevant) {
                            output += "dexInsn=$index:${instruction.opcode.name}$refText$operands"
                            emitted++
                        }
                    }
                }
            }
        } catch (error: Throwable) {
            output += "dexDisassemblyError=$entryName;${error.javaClass.simpleName}:${error.message.orEmpty().take(180)}"
        } finally {
            temp.delete()
        }
    }
}
