package pt.catharsys.digiautostart

enum class LaunchMode {
    ALWAYS,
    ONLY_IF_LAST_APP
}

/**
 * Pure decision logic, kept separate so it can be unit-tested.
 * This does not observe standby or launch activities by itself.
 */
object LaunchPolicy {
    fun shouldLaunch(
        mode: LaunchMode,
        lastForegroundPackage: String?,
        targetPackage: String
    ): Boolean = when (mode) {
        LaunchMode.ALWAYS -> true
        LaunchMode.ONLY_IF_LAST_APP -> lastForegroundPackage == targetPackage
    }
}
