package net.corda.sandboxhooks

import net.corda.sandbox.CpkSandbox
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.framework.Bundle
import org.osgi.framework.FrameworkUtil
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

/** Tests the ability to have different copies of the same library in different sandboxes. */
@ExtendWith(ServiceExtension::class)
class SandboxBundleDifferentLibrariesTest {
    companion object {
        const val INVOKE_LIBRARY_VERSION_FLOW_CPK_1_CLASS = "com.example.sandbox.cpk1.LibraryVersionFlow"
        const val INVOKE_LIBRARY_VERSION_FLOW_CPK_2_CLASS = "com.example.sandbox.cpk2.LibraryVersionFlow"
        const val CPK_1_JODATIME_VERSION = "2.10.10"
        const val CPK_2_JODATIME_VERSION = "2.10.9"

        @InjectService(timeout = 1000)
        lateinit var sandboxLoader: SandboxLoader
    }

    @Suppress("SameParameterValue")
    private fun getBundle(className: String, sandbox: CpkSandbox): Bundle {
        return FrameworkUtil.getBundle(sandbox.loadClassFromCordappBundle(className))
    }

    @Test
    fun checkDifferentCPKsHaveDifferentLibraryBundlesButWithSameSymbolicName() {
        val sandbox1 = sandboxLoader.group1.getSandbox(sandboxLoader.cpk1.metadata.id)
        val sandbox2 = sandboxLoader.group1.getSandbox(sandboxLoader.cpk2.metadata.id)
        val sandbox3 = sandboxLoader.group2.getSandbox(sandboxLoader.cpk3.metadata.id)

        val libraryBundle1 = getBundle(LIBRARY_QUERY_CLASS, sandbox1)
        val libraryBundle2 = getBundle(LIBRARY_QUERY_CLASS, sandbox2)
        val libraryBundle3 = getBundle(LIBRARY_QUERY_CLASS, sandbox3)

        // Two sandboxes in the same sandbox group have a different copy of the same library.
        assertThat(libraryBundle1.symbolicName).isEqualTo(libraryBundle2.symbolicName)
        assertThat(libraryBundle1.bundleId).isNotEqualTo(libraryBundle2.bundleId)

        // Two sandboxes in a different sandbox group have a different copy of the same library.
        assertThat(libraryBundle1.symbolicName).isEqualTo(libraryBundle3.symbolicName)
        assertThat(libraryBundle1.bundleId).isNotEqualTo(libraryBundle3.bundleId)
    }

    @Test
    fun checkDifferentCPKsCanUseDifferentVersionsOfTheSameLibrary() {
        // This flow returns a string containing the version of the Joda-Time library used.
        val cpkOneReturnString = sandboxLoader.runFlow<String>(INVOKE_LIBRARY_VERSION_FLOW_CPK_1_CLASS, sandboxLoader.group1)
        val cpkTwoReturnString = sandboxLoader.runFlow<String>(INVOKE_LIBRARY_VERSION_FLOW_CPK_2_CLASS, sandboxLoader.group1)

        assertTrue(cpkOneReturnString.contains(CPK_1_JODATIME_VERSION))
        assertTrue(cpkTwoReturnString.contains(CPK_2_JODATIME_VERSION))
    }
}
