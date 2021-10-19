package net.corda.securitymanager.osgi

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

/** Tests the permissions of unsandboxed bundles. */
@ExtendWith(ServiceExtension::class)
class UnsandboxedPermissionTests {
    companion object {
        @InjectService(timeout = 1000)
        lateinit var sandboxLoader: SandboxLoader
    }

    @Test
    fun `unsandboxed bundle has all OSGi permissions`() {
        assertDoesNotThrow {
            sandboxLoader.unsandboxedOsgiInvoker.apply {
                getBundleContext()
                startBundle()
                installBundle()
                addListener()
                loadClass()
                getLocation()
                refreshBundles()
                adaptBundle()
                getService()
            }
        }
    }
}