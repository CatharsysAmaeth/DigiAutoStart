package pt.catharsys.digiautostart

import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.iface.instruction.NarrowLiteralInstruction
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
        "/overlay/FeatureFactory",
        "/SettingsFragmentProvider;",
        "/accessibility/AccessibilityServiceFragment;",
        "/accessibility/AccessibilityServiceConfirmationFragment;",
        "/ZteKeyInterceptor",
        "/TvSettingsService"
    )
    private val methodTargets = Regex(
        "(?i)(onCreate|createSettingsFragment|getAvailableFlavors|getFlavor|getFeatureFactory|" +
            "getSettingsFragmentProvider|newSettingsFragment|prepareArgs|onCreatePreferences|" +
            "onPreferenceTreeClick|onResume|setAccessibilityServiceState|finish|isStartupVerificationRequired|onKey|intercept|dispatchKey|handleKey|bindService|onBind|onTransact)"
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
                    clazz.type.contains("TvSettingsActivity") -> 0
                    clazz.type.contains("FlavorUtils") -> 1
                    clazz.type.contains("oemlink/AccessibilityServiceActivity") -> 2
                    clazz.type.contains("oemlink/AccessibilitySettingsActivity") -> 3
                    clazz.type.contains("FeatureFactory") -> 4
                    clazz.type.contains("AccessibilityServiceFragment") -> 5
                    clazz.type.contains("ZteKeyInterceptor") -> 6
                    else -> 7
                }
            }
            output += "dexDisassembly=$entryName;selectedClasses=${selected.size}"
            val settingsResources = runCatching {
                context.packageManager.getResourcesForApplication("com.android.tv.settings")
            }.getOrNull()
            val queriedResources = linkedSetOf<String>()
            for (clazz in selected) {
                if (output.size >= 100) break
                output += "dexClass=${clazz.type};super=${clazz.superclass ?: "none"}"
                if (clazz.type.contains("FlavorUtils")) {
                    clazz.fields.filter { it.name.contains("FLAVOR", true) || it.name.contains("MASK", true) }
                        .forEach { field ->
                            val encoded = field.initialValue
                            val value = when (encoded) {
                                is IntEncodedValue -> encoded.value.toString()
                                null -> "unset"
                                else -> encoded.toString()
                            }
                            output += "dexStaticField=${field.name}:${field.type}=$value"
                        }
                }
                val methods = clazz.methods.filter { methodTargets.containsMatchIn(it.name) }
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
                    if (output.size >= 100) break
                    val impl = method.implementation ?: continue
                    val signature = method.parameterTypes.joinToString("") { it.toString() }
                    val instructions = impl.instructions.toList()
                    output += "dexMethod=${clazz.type}->${method.name}($signature)${method.returnType};ins=${instructions.size}"
                    var emitted = 0
                    val maxInstructions = if (method.name in setOf(
                        "onCreate", "finish", "getFlavor", "getFeatureFactory",
                        "createSettingsFragment", "getAvailableFlavors", "onCreatePreferences"
                    )) 14 else 8
                    for ((index, instruction) in instructions.withIndex()) {
                        if (emitted >= maxInstructions || output.size >= 100) break
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
                        val operands = buildString {
                            if (instruction is NarrowLiteralInstruction) append(" literal=${instruction.narrowLiteral}")
                            if (instruction is WideLiteralInstruction) append(" literal=${instruction.wideLiteral}")
                            if (instruction is OffsetInstruction) append(" offset=${instruction.codeOffset}")
                        }
                        val relevant = ref is MethodReference || ref is FieldReference ||
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
