package pt.catharsys.digiautostart

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchPolicyTest {
    private val target = "ro.digionline.tv"

    @Test
    fun alwaysModeLaunchesRegardlessOfLastApp() {
        assertTrue(LaunchPolicy.shouldLaunch(LaunchMode.ALWAYS, "com.android.settings", target))
        assertTrue(LaunchPolicy.shouldLaunch(LaunchMode.ALWAYS, null, target))
    }

    @Test
    fun lastAppModeOnlyLaunchesWhenTargetWasLast() {
        assertTrue(LaunchPolicy.shouldLaunch(LaunchMode.ONLY_IF_LAST_APP, target, target))
        assertFalse(LaunchPolicy.shouldLaunch(LaunchMode.ONLY_IF_LAST_APP, "com.android.settings", target))
        assertFalse(LaunchPolicy.shouldLaunch(LaunchMode.ONLY_IF_LAST_APP, null, target))
    }
}
