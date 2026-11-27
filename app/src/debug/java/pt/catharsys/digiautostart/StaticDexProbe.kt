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
            "onPreferenceTreeClick|onResume|setAccessibilityServiceState|isTwoPanel|onKey|intercept|dispatchKey|handleKey|bindService|onBind|onTransact)"
    )

    fun inspect(bytes: ByteArray, entryName: String, output: MutableList<String>, cacheDir: File) {
        val temp = File.createTempFile("dada-tvsettings-", ".dex", cacheDir)
        try {
            temp.writeBytes(bytes)
            val dex = DexFileFactory.loadDexFile(temp, Opcodes.getDefault())
            val selected = dex.classes.filter { clazz ->
                classTargets.any { clazz.type.contains(it, ignoreCase = true) }
            }.sortedBy { it.type }
            output += "dexDisassembly=$entryName;selectedClasses=${selected.size}"
            for (clazz in selected) {
                if (output.size >= 100) break
                output += "dexClass=${clazz.type};super=${clazz.superclass ?: "none"}"
                val methods = clazz.methods.filter { methodTargets.containsMatchIn(it.name) }
                    .sortedBy { it.name }
                for (method in methods) {
                    if (output.size >= 100) break
                    val impl = method.implementation ?: continue
                    val signature = method.parameterTypes.joinToString("") { it.toString() }
                    val instructions = impl.instructions.toList()
                    output += "dexMethod=${clazz.type}->${method.name}($signature)${method.returnType};ins=${instructions.size}"
                    var emitted = 0
                    for ((index, instruction) in instructions.withIndex()) {
                        if (emitted >= 18 || output.size >= 100) break
                        val ref = (instruction as? ReferenceInstruction)?.reference
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
