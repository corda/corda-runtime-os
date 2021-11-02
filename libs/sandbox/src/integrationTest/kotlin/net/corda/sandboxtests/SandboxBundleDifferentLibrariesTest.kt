package net.corda.sandboxtests

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

/** Tests the ability to have different copies of the same library in different sandboxes. */
@ExtendWith(ServiceExtension::class)
class SandboxBundleDifferentLibrariesTest {
    companion object {
        @InjectService(timeout = 1000)
        lateinit var sandboxLoader: SandboxLoader
    }

    @Test
    fun `different CPKs in the same sandbox group can use different versions of the same library`() {
        // This flow returns a string containing the version of the Joda-Time library used.
        val cpkOneReturnString = runFlow<String>(sandboxLoader.group1, LIBRARY_VERSION_FLOW_CPK_1)
        val cpkTwoReturnString = runFlow<String>(sandboxLoader.group1, LIBRARY_VERSION_FLOW_CPK_2)

        assertTrue(cpkOneReturnString.contains(CPK_1_JODATIME_VERSION))
        assertTrue(cpkTwoReturnString.contains(CPK_2_JODATIME_VERSION))
    }
}
