@file:Suppress("deprecation")
package net.corda.securitymanager.internal

import java.io.FilePermission
import java.nio.file.Paths
import java.security.AccessControlException
import net.corda.securitymanager.SecurityManagerService
import net.corda.testing.securitymanager.denyPermissions
import net.corda.testing.securitymanager.grantPermissions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import org.osgi.framework.BundleContext
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
class CanonicalTempDirectoryPermissionsTest {
    @Test
    fun testBundleDataPermissions(
        @InjectBundleContext
        bundleContext: BundleContext,
        @InjectService
        securityManagerService: SecurityManagerService
    ) {
        val tempDir = Paths.get(System.getProperty("java.io.tmpdir") ?: fail("java.io.tmpdir not defined"))
        val canonicalTempDir = Paths.get(tempDir.toFile().canonicalPath)
        val otherDir = tempDir.fileSystem.rootDirectories.first()

        securityManagerService.startRestrictiveMode()
        securityManagerService.denyPermissions(bundleContext.bundle.location, listOf(
            FilePermission("<<ALL FILES>>", "read,write,execute,delete,readlink")
        ))
        securityManagerService.grantPermissions(bundleContext.bundle.location, listOf(
            FilePermission(canonicalTempDir.resolve("-").toString(), "read"),
        ))

        val securityManager = System.getSecurityManager() ?: fail("Security not enabled")

        // Check we can access a temporary file via a non-canonical path.
        val tempFile = tempDir.resolve("file.tmp")
        assertDoesNotThrow("Cannot access $tempFile") {
            securityManager.checkPermission(FilePermission(tempFile.toString(), "read"))
        }

        // Check our "deny everything else" policy works too.
        val otherFile = otherDir.resolve("path").resolve("file.txt")
        assertThrows<AccessControlException>("Unwanted access to $otherFile") {
            securityManager.checkPermission(FilePermission(otherFile.toString(), "read"))
        }
    }
}
