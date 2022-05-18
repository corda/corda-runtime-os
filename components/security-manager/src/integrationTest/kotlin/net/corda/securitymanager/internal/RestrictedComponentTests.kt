package net.corda.securitymanager.internal

import net.corda.securitymanager.SecurityManagerService
import net.corda.testing.securitymanager.denyPermissions
import net.corda.testing.securitymanager.grantPermissions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.framework.Bundle
import org.osgi.framework.Constants.SYSTEM_BUNDLE_ID
import org.osgi.framework.FrameworkUtil
import org.osgi.framework.ServicePermission
import org.osgi.framework.ServicePermission.GET
import org.osgi.service.component.runtime.ServiceComponentRuntime
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class)
class RestrictedComponentTests {
    companion object {
        private val scrPermission = ServicePermission(ServiceComponentRuntime::class.java.name, GET)
    }

    @InjectService(timeout = 1000)
    lateinit var securityManagerService: SecurityManagerService

    private lateinit var cpkBundle: Bundle

    @BeforeEach
    fun reset() {
        securityManagerService.startRestrictiveMode()

        // CPK installation "on the cheap"!
        val frameworkContext = FrameworkUtil.getBundle(this::class.java)
            .bundleContext
            .getBundle(SYSTEM_BUNDLE_ID)
            .bundleContext
        cpkBundle = frameworkContext.installBundle(
            "sandbox.scr.cpk",
            this::class.java.classLoader.getResourceAsStream("sandbox-scr-cpk.jar")
        )
    }

    @AfterEach
    fun done() {
        try {
            cpkBundle.stop()
        } finally {
            cpkBundle.uninstall()
        }
    }

    @Test
    fun testWeCannotResolveScrReferenceWithoutPermission() {
        securityManagerService.denyPermissions(cpkBundle.location, listOf(scrPermission))
        cpkBundle.start()
        assertNull(cpkBundle.registeredServices)
    }

    @Test
    fun testWeCanResolveScrReferenceWithPermission() {
        securityManagerService.grantPermissions(cpkBundle.location, listOf(scrPermission))
        cpkBundle.start()
        assertThat(cpkBundle.registeredServices).hasSize(1)
    }
}
