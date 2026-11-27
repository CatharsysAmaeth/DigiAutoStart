package pt.catharsys.digiautostart

import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.iface.instruction.ReferenceInstruction
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
            "onPreferenceTreeClick|onResume|setAccessibilityServiceState|isTwoPanel)"
    )

    fun inspect(bytes: ByteArray, entryName: String, output: MutableList<String>) {
        val temp = File.createTempFile("dada-tvsettings-", ".dex")
        try {
            temp.writeBytes(bytes)
            val dex = DexFileFactory.loadDexFile(temp, Opcodes.getDefault())
            val selected = dex.classes.filter { clazz ->
                classTargets.any { clazz.type.contains(it, ignoreCase = true) }
            }.sortedBy { it.type }
            output += "dexDisassembly=$entryName;selectedClasses=${selected.size}"
            for (clazz in selected) {
                if (output.size >= 180) break
                output += "dexClass=${clazz.type};super=${clazz.superclass ?: "none"}"
                val methods = clazz.methods.filter { methodTargets.containsMatchIn(it.name) }
                    .sortedBy { it.name }
                for (method in methods) {
                    if (output.size >= 180) break
                    val impl = method.implementation ?: continue
                    val signature = method.parameterTypes.joinToString("") { it.type }
                    val instructions = impl.instructions.toList()
                    output += "dexMethod=${clazz.type}->${method.name}($signature)${method.returnType};ins=${instructions.size}"
                    var emitted = 0
                    for ((index, instruction) in instructions.withIndex()) {
                        if (emitted >= 36 || output.size >= 180) break
                        val ref = (instruction as? ReferenceInstruction)?.reference
                        val refText = when (ref) {
                            is StringReference -> " str=${ref.string.take(220)}"
                            is MethodReference -> " method=${ref.definingClass}->${ref.name}(" +
                                ref.parameterTypes.joinToString("") { it.toString() } + ")${ref.returnType}"
                            is TypeReference -> " type=${ref.type}"
                            is FieldReference -> " field=${ref.definingClass}->${ref.name}:${ref.type}"
                            else -> ""
                        }
                        val lower = refText.lowercase()
                        val relevant = ref == null || lower.contains("accessibility") ||
                            lower.contains("flavor") || lower.contains("fragment") ||
                            lower.contains("factory") || lower.contains("settings") ||
                            lower.contains("zte") || lower.contains("service") ||
                            lower.contains("activity") || lower.contains("intent") ||
                            lower.contains("parcel") || lower.contains("enabled") ||
                            lower.contains("unsupported")
                        if (relevant) {
                            output += "dexInsn=$index:${instruction.opcode.name}$refText"
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
