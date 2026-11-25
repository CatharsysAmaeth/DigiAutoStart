package pt.catharsys.digiautostart

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.graphics.Rect
import android.os.SystemClock

/**
 * Debug-only observer. It is restricted to the official DIGI TV package and
 * records a bounded view-tree summary only while diagnostics collection is on.
 * It deliberately does not inject key events or click controls.
 */
class DigiTvAccessibilityProbeService : AccessibilityService() {
    private var lastContentSnapshotMs = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        DiagnosticsFeature.record(this, "digi_tv_accessibility_probe_connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!DiagnosticsFeature.isCapturing(this)) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName != TARGET_PACKAGE) return

        val now = SystemClock.elapsedRealtime()
        val shouldSnapshot = when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_SELECTED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> true
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (now - lastContentSnapshotMs >= CONTENT_SNAPSHOT_INTERVAL_MS) {
                    lastContentSnapshotMs = now
                    true
                } else false
            }
            else -> false
        }
        // Ignore other events and throttle content-change events so diagnostics
        // do not add unnecessary work while the DIGI TV interface is starting.
        if (!shouldSnapshot) return

        val eventText = if (event.isPassword) "[password-field]" else event.text.orEmpty()
            .mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
            .joinToString("|")
            .let(::redact)
            .take(MAX_EVENT_TEXT)
        DiagnosticsFeature.record(
            this,
            "digi_tv_accessibility_event",
            "type=${eventTypeName(event.eventType)};class=${event.className};contentChangeTypes=${event.contentChangeTypes};text=$eventText"
        )
        if (shouldSnapshot) recordWindowSnapshot()
    }

    private fun recordWindowSnapshot() {
        val root = rootInActiveWindow
        if (root == null) {
            DiagnosticsFeature.record(this, "digi_tv_accessibility_tree", "root=null")
            return
        }
        val lines = ArrayList<String>()
        appendNode(root, 0, lines)
        DiagnosticsFeature.record(
            this,
            "digi_tv_accessibility_tree",
            "nodes=${lines.size};tree=${lines.joinToString(" || ").take(MAX_TREE_TEXT)}"
        )
    }

    private fun appendNode(node: AccessibilityNodeInfo, depth: Int, lines: MutableList<String>) {
        if (depth > MAX_DEPTH || lines.size >= MAX_NODES) return
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val text = if (node.isPassword) "[password-field]" else redact(node.text?.toString().orEmpty()).take(MAX_NODE_TEXT)
        val description = if (node.isPassword) "[password-field]" else redact(node.contentDescription?.toString().orEmpty()).take(MAX_NODE_TEXT)
        val viewId = node.viewIdResourceName.orEmpty().take(100)
        lines += "d=$depth;class=${node.className};id=$viewId;text=$text;desc=$description;password=${node.isPassword};click=${node.isClickable};focusable=${node.isFocusable};focused=${node.isFocused};selected=${node.isSelected};bounds=$bounds"
        for (index in 0 until node.childCount) {
            if (lines.size >= MAX_NODES) break
            val child = node.getChild(index) ?: continue
            appendNode(child, depth + 1, lines)
        }
    }

    private fun eventTypeName(type: Int): String = when (type) {
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "window_state_changed"
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "window_content_changed"
        AccessibilityEvent.TYPE_VIEW_FOCUSED -> "view_focused"
        AccessibilityEvent.TYPE_VIEW_SELECTED -> "view_selected"
        AccessibilityEvent.TYPE_WINDOWS_CHANGED -> "windows_changed"
        else -> "type_$type"
    }

    private fun redact(value: String): String = value
        .replace('\n', ' ')
        .replace('\r', ' ')
        .replace(Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE), "[email]")
        .replace(Regex("(?<![A-Z0-9])(?:\\+?\\d[\\d .()/-]{7,}\\d)(?![A-Z0-9])"), "[phone]")

    override fun onInterrupt() {
        DiagnosticsFeature.record(this, "digi_tv_accessibility_probe_interrupted")
    }

    companion object {
        private const val TARGET_PACKAGE = "ro.digionline.tv"
        private const val CONTENT_SNAPSHOT_INTERVAL_MS = 1200L
        private const val MAX_DEPTH = 7
        private const val MAX_NODES = 70
        private const val MAX_NODE_TEXT = 100
        private const val MAX_EVENT_TEXT = 500
        private const val MAX_TREE_TEXT = 6500
    }
}
