package net.corda.sandboxhooks

import net.corda.sandbox.CpkSandbox
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
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
        @InjectService(timeout = 1000)
        lateinit var sandboxLoader: SandboxLoader
    }

    @Suppress("SameParameterValue")
    private fun getBundle(className: String, sandbox: CpkSandbox): Bundle {
        return FrameworkUtil.getBundle(sandbox.loadClassFromCordappBundle(className))
    }

    @Test
    fun checkDifferentCPKsHaveDifferentLibraryBundlesButWithSameSymbolicName() {
        val libraryBundle1 = getBundle(LIBRARY_QUERY_CLASS, sandboxLoader.sandbox1)
        val libraryBundle2 = getBundle(LIBRARY_QUERY_CLASS, sandboxLoader.sandbox2)
        val libraryBundle3 = getBundle(LIBRARY_QUERY_CLASS, sandboxLoader.sandbox3)

        // Two sandboxes in the same sandbox group have a different copy of the same library.
        assertEquals(libraryBundle1.symbolicName, libraryBundle2.symbolicName)
        assertNotEquals(libraryBundle1.bundleId, libraryBundle2.bundleId)

        // Two sandboxes in a different sandbox group have a different copy of the same library.
        assertEquals(libraryBundle1.symbolicName, libraryBundle3.symbolicName)
        assertNotEquals(libraryBundle1.bundleId, libraryBundle3.bundleId)
    }

    @Test
    fun checkDifferentCPKsCanUseDifferentVersionsOfTheSameLibrary() {
        // This flow returns a string containing the version of the Joda-Time library used.
        val cpkOneReturnString = sandboxLoader.runFlow<String>(LIBRARY_VERSION_FLOW_CPK_1, sandboxLoader.group1)
        val cpkTwoReturnString = sandboxLoader.runFlow<String>(LIBRARY_VERSION_FLOW_CPK_2, sandboxLoader.group1)

        assertTrue(cpkOneReturnString.contains(CPK_1_JODATIME_VERSION))
        assertTrue(cpkTwoReturnString.contains(CPK_2_JODATIME_VERSION))
    }
}
