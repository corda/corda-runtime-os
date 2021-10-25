package net.corda.sandboxhooks

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

/** Tests the ability to have different versions of the same library in different sandboxes in the same sandbox group. */
@ExtendWith(ServiceExtension::class)
class SandboxBundleMultipleLibraryVersionsTest {
    companion object {
        const val INVOKE_LIBRARY_VERSION_FLOW_CPK_1_CLASS = "com.example.sandbox.cpk1.LibraryVersionFlow"
        const val INVOKE_LIBRARY_VERSION_FLOW_CPK_2_CLASS = "com.example.sandbox.cpk2.LibraryVersionFlow"
        const val CPK_1_JODATIME_VERSION = "2.10.10"
        const val CPK_2_JODATIME_VERSION = "2.10.9"

        @InjectService(timeout = 1000)
        lateinit var sandboxLoader: SandboxLoader
    }

    @Test
    fun checkDifferentCPKsUseDifferentLibraryVersions() {
        val cpkOneReturnString = sandboxLoader.runFlow<String>(INVOKE_LIBRARY_VERSION_FLOW_CPK_1_CLASS, sandboxLoader.group1)
        assertTrue(cpkOneReturnString.contains(CPK_1_JODATIME_VERSION))

        val cpkTwoReturnString = sandboxLoader.runFlow<String>(INVOKE_LIBRARY_VERSION_FLOW_CPK_2_CLASS, sandboxLoader.group1)
        assertTrue(cpkTwoReturnString.contains(CPK_2_JODATIME_VERSION))
    }
}
