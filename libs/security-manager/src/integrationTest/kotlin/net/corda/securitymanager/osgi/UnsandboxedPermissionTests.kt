package net.corda.securitymanager.osgi

import net.corda.securitymanager.osgiinvoker.OsgiInvoker
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.framework.Bundle
import org.osgi.framework.FrameworkUtil
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.net.URL
import java.security.AccessControlException
import kotlin.random.Random

/** Tests the permissions of unsandboxed bundles. */
@ExtendWith(ServiceExtension::class)
class UnsandboxedPermissionTests {
    companion object {
        @InjectService(timeout = 1000)
        lateinit var unsandboxedOsgiInvoker: OsgiInvoker
    }

    @Test
    fun `unsandboxed bundle has all OSGi permissions`() {
        assertDoesNotThrow {
            unsandboxedOsgiInvoker.apply {
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