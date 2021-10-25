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

    private fun assertDistinctDuplicates(bundle1: Bundle, bundle2: Bundle) {
        assertThat(bundle1.symbolicName).isEqualTo(bundle2.symbolicName)
        assertThat(bundle1.bundleId).isNotEqualTo(bundle2.bundleId)
    }

    @Test
    fun checkDifferentCPKsHaveDifferentLibraryBundlesButWithSameSymbolicName() {
        val sandbox1 = sandboxLoader.group1.getSandbox(sandboxLoader.cpk1.metadata.id)
        val sandbox2 = sandboxLoader.group1.getSandbox(sandboxLoader.cpk2.metadata.id)
        val sandbox3 = sandboxLoader.group2.getSandbox(sandboxLoader.cpk3.metadata.id)

        val library1 = getBundle(LIBRARY_QUERY_CLASS, sandbox1)
        val library2 = getBundle(LIBRARY_QUERY_CLASS, sandbox2)
        val library3 = getBundle(LIBRARY_QUERY_CLASS, sandbox3)

        // Two sandboxes in the same sandbox group have a different copy of the same library.
        assertDistinctDuplicates(library1, library2)

        // Two sandboxes in a different sandbox group have a different copy of the same library.
        assertDistinctDuplicates(library1, library3)
    }

    @Test
    fun checkDifferentCPKsCanUseDifferentVersionsOfTheSameLibrary() {
        val cpkOneReturnString = sandboxLoader.runFlow<String>(INVOKE_LIBRARY_VERSION_FLOW_CPK_1_CLASS, sandboxLoader.group1)
        assertTrue(cpkOneReturnString.contains(CPK_1_JODATIME_VERSION))

        val cpkTwoReturnString = sandboxLoader.runFlow<String>(INVOKE_LIBRARY_VERSION_FLOW_CPK_2_CLASS, sandboxLoader.group1)
        assertTrue(cpkTwoReturnString.contains(CPK_2_JODATIME_VERSION))
    }
}
