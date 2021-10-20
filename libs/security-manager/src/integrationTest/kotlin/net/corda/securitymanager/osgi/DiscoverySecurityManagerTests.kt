package net.corda.securitymanager.osgi

import net.corda.securitymanager.SecurityManagerService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

/** Tests the `DiscoverySecurityManager`. */
@ExtendWith(ServiceExtension::class)
class DiscoverySecurityManagerTests {
    companion object {
        @InjectService(timeout = 1000)
        lateinit var securityManagerService: SecurityManagerService
    }

    @Suppress("unused")
    @BeforeEach
    fun reset() {
        securityManagerService.start()
    }

    @Test
    fun `discovery mode grants all OSGi permissions`() {
        assertDoesNotThrow {
            // This permission stands in for all permissions.
            System.getenv()
        }
    }

    // TODO - More tests around trying to deny perms, etc.
}