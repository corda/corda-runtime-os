package net.corda.permissions.service.internal

import net.corda.libs.permission.PermissionValidator
import net.corda.libs.permissions.cache.PermissionCache
import net.corda.libs.permissions.manager.PermissionManager
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.permissions.cache.PermissionCacheService
import net.corda.permissions.management.PermissionManagementService
import net.corda.rpc.permissions.PermissionValidationService
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class PermissionServiceComponentEventHandlerTest {
    private val permissionCache = mock<PermissionCache>()
    private val permissionManager = mock<PermissionManager>()
    private val permissionValidator = mock<PermissionValidator>()

    private val registrationHandle = mock<RegistrationHandle>()

    private val coordinator = mock<LifecycleCoordinator>()

    private val permissionManagementService = mock<PermissionManagementService>()
    private val permissionValidationService = mock<PermissionValidationService>()
    private val permissionCacheService = mock<PermissionCacheService>()

    private val handler = PermissionServiceComponentEventHandler(
        permissionManagementService,
        permissionValidationService,
        permissionCacheService
    )

    @BeforeEach
    fun setUp() {
        whenever(permissionCacheService.permissionCache).thenReturn(permissionCache)
        whenever(permissionManagementService.permissionManager).thenReturn(permissionManager)
        whenever(permissionValidationService.permissionValidator).thenReturn(permissionValidator)

        whenever(
            coordinator.followStatusChangesByName(
                setOf(
                    LifecycleCoordinatorName.forComponent<PermissionCacheService>(),
                    LifecycleCoordinatorName.forComponent<PermissionManagementService>(),
                    LifecycleCoordinatorName.forComponent<PermissionValidationService>()
                )
            )
        ).thenReturn(registrationHandle)

        whenever(coordinator.isRunning).thenReturn(true)
    }

    @Test
    fun `processing a start event causes the service to follow status changes for child components and starts child services`() {
        assertNull(handler.registration)

        handler.processEvent(StartEvent(), coordinator)

        assertNotNull(handler.registration)

        verify(permissionCacheService).start()
        verify(permissionManagementService).start()
        verify(permissionValidationService).start()
    }

    @Test
    fun `processing an UP event sets the coordinator's status to UP`() {
        handler.processEvent(StartEvent(), coordinator)
        handler.processEvent(RegistrationStatusChangeEvent(registrationHandle, LifecycleStatus.UP), coordinator)

        verify(coordinator).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `processing a DOWN event from the validation service when the parent is started sets the status to DOWN`() {
        handler.processEvent(StartEvent(), coordinator)
        handler.processEvent(RegistrationStatusChangeEvent(registrationHandle, LifecycleStatus.UP), coordinator)

        handler.processEvent(RegistrationStatusChangeEvent(registrationHandle, LifecycleStatus.DOWN), coordinator)

        verify(coordinator).updateStatus(LifecycleStatus.DOWN)
    }

    @Test
    fun `processing a ERROR event from the cache service when the parent is started sets the status to ERROR`() {
        handler.processEvent(StartEvent(), coordinator)
        handler.processEvent(RegistrationStatusChangeEvent(registrationHandle, LifecycleStatus.UP), coordinator)

        handler.processEvent(RegistrationStatusChangeEvent(registrationHandle, LifecycleStatus.ERROR), coordinator)

        verify(coordinator).updateStatus(LifecycleStatus.ERROR)
    }

    @Test
    fun `processing a stop event stops the service's dependencies`() {
        handler.processEvent(StartEvent(), coordinator)
        handler.processEvent(RegistrationStatusChangeEvent(registrationHandle, LifecycleStatus.UP), coordinator)

        assertNotNull(handler.registration)

        handler.processEvent(StopEvent(), coordinator)

        assertNull(handler.registration)

        verify(permissionValidationService).stop()
        verify(permissionManagementService).stop()
        verify(permissionCacheService).stop()
        verify(coordinator).updateStatus(LifecycleStatus.DOWN)
    }
}