package net.corda.components.rbac

import net.corda.rest.security.read.RestSecurityManager
import net.corda.rest.security.read.rbac.RBACSecurityManager
import net.corda.libs.permission.PermissionValidator
import net.corda.libs.permissions.manager.BasicAuthenticationService
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.permissions.management.PermissionManagementService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RBACSecurityManagerServiceTest {

    private val permissionManagementService = mock<PermissionManagementService>()
    private val permissionServiceRegistration = mock<RegistrationHandle>()
    private val coordinatorFactory = mock<LifecycleCoordinatorFactory>()
    private val coordinator = mock<LifecycleCoordinator>()
    private val permissionValidator = mock<PermissionValidator>()
    private val basicAuthenticationService = mock<BasicAuthenticationService>()

    private val service = RBACSecurityManagerService(coordinatorFactory, permissionManagementService)

    @BeforeEach
    fun setUp() {
        service.coordinator = coordinator
        whenever(
            coordinator.followStatusChangesByName(
                setOf(
                    LifecycleCoordinatorName.forComponent<PermissionManagementService>()
                )
            )
        ).thenReturn(permissionServiceRegistration)

        whenever(permissionManagementService.permissionValidator).thenReturn(permissionValidator)
        whenever(permissionManagementService.basicAuthenticationService).thenReturn(basicAuthenticationService)
        whenever(coordinator.isRunning).thenReturn(true)
    }

    @Test
    fun `process start event follows permission service`() {
        assertNull(service.registration)

        service.processEvent(StartEvent(), coordinator)

        assertEquals(permissionServiceRegistration, service.registration)
    }

    @Test
    fun `process registration UP event creates the RBAC security manager and sets the status to UP`() {
        val e = assertThrows<IllegalArgumentException> { service.securityManager }
        assertEquals("Security Manager has not been initialized.", e.message)

        service.processEvent(RegistrationStatusChangeEvent(permissionServiceRegistration, LifecycleStatus.UP), coordinator)

        assertNotNull(service.securityManager)
        assertTrue(service.securityManager is RBACSecurityManager)
    }

    @Test
    fun `process registration DOWN event posts stop event to the coordinator`() {
        val restSecurityManager = mock<RestSecurityManager>()
        service.innerSecurityManager = restSecurityManager
        service.processEvent(RegistrationStatusChangeEvent(permissionServiceRegistration, LifecycleStatus.DOWN), coordinator)
        verify(restSecurityManager).stop()
    }

    @Test
    fun `process registration ERROR event posts stop event with errored to the coordinator`() {
        service.processEvent(RegistrationStatusChangeEvent(permissionServiceRegistration, LifecycleStatus.ERROR), coordinator)

        verify(coordinator).postEvent(StopEvent(true))
    }

    @Test
    fun `isRunning should return true if coordinator is running`() {
        whenever(coordinator.isRunning).thenReturn(true)
        assertTrue(coordinator.isRunning)
    }

    @Test
    fun `isRunning should return false if coordinator is not running`() {
        whenever(coordinator.isRunning).thenReturn(false)
        assertFalse(coordinator.isRunning)
    }

    @Test
    fun `get securityManager should throw if service's coordinator is not running`() {
        whenever(coordinator.isRunning).thenReturn(false)
        val e = assertThrows<IllegalArgumentException> { service.securityManager }
        assertEquals("Security Manager is not running.", e.message)
    }

    @Test
    fun `get securityManager should throw if security manager has not been initialized`() {
        whenever(coordinator.isRunning).thenReturn(true)
        val e = assertThrows<IllegalArgumentException> { service.securityManager }
        assertEquals("Security Manager has not been initialized.", e.message)
    }
}
