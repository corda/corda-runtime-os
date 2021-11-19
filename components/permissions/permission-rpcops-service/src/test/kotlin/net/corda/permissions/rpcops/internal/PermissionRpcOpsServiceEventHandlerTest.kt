package net.corda.permissions.rpcops.internal

import net.corda.libs.permission.PermissionValidator
import net.corda.libs.permissions.endpoints.v1.user.impl.UserEndpointImpl
import net.corda.libs.permissions.manager.PermissionManager
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.permissions.service.PermissionService
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class PermissionRpcOpsServiceEventHandlerTest {

    private val permissionService = mock<PermissionService>()
    private val coordinator = mock<LifecycleCoordinator>()
    private val registration = mock<RegistrationHandle>()

    private val handler = PermissionRpcOpsServiceEventHandler(permissionService)

    @BeforeEach
    fun setUp() {
        whenever(coordinator.followStatusChangesByName(any())).thenReturn(registration)
    }

    private fun startService() {
        handler.processEvent(StartEvent(), coordinator)
        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator)
    }

    @Test
    fun `processing a start event creates registration`() {
        assertNull(handler.registration)

        handler.processEvent(StartEvent(), coordinator)

        assertNotNull(handler.registration)
    }

    @Test
    fun `processing an UP event from permission service starts the user endpoint`() {
        assertNull(handler.userEndpoint)

        startService()

        verify(coordinator).updateStatus(LifecycleStatus.UP)
        assertNotNull(handler.userEndpoint)
    }

    @Test
    fun `processing a DOWN event from permission service sets the coordinator to DOWN but does not null out the endpoints`() {
        assertNull(handler.userEndpoint)

        startService()

        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.DOWN), coordinator)

        verify(coordinator).updateStatus(LifecycleStatus.DOWN)
        assertNotNull(handler.userEndpoint)
    }

    @Test
    fun `processing a ERROR event from permission service sets the coordinator to ERROR but does not null out the endpoints`() {
        assertNull(handler.userEndpoint)

        startService()

        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.ERROR), coordinator)

        verify(coordinator).updateStatus(LifecycleStatus.ERROR)
        assertNotNull(handler.userEndpoint)
    }

    @Test
    fun `processing a stop event stops the service's dependencies`() {
        assertNull(handler.registration)

        startService()

        handler.processEvent(StopEvent(), coordinator)

        verify(coordinator).updateStatus(LifecycleStatus.DOWN)
        assertNull(handler.registration)
        assertNotNull(handler.userEndpoint)
    }

    @Test
    fun `processing UP event from permission service when user endpoint is not null will use setters to update validator and manager`() {
        val permissionManager = mock<PermissionManager>()
        val permissionValidator = mock<PermissionValidator>()
        whenever(permissionService.permissionManager).thenReturn(permissionManager)
        whenever(permissionService.permissionValidator).thenReturn(permissionValidator)

        assertNull(handler.registration)

        handler.processEvent(StartEvent(), coordinator)

        val mockEndpoint = mock<UserEndpointImpl>()
        handler.userEndpoint = mockEndpoint

        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator)

        verify(mockEndpoint).setPermissionManager(permissionManager)
        verify(mockEndpoint).setPermissionValidator(permissionValidator)
        assertNotNull(handler.userEndpoint)
    }



}