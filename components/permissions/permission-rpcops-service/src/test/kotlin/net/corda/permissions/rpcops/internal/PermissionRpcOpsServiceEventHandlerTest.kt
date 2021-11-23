package net.corda.permissions.rpcops.internal

import net.corda.libs.permission.PermissionValidator
import net.corda.libs.permissions.endpoints.v1.user.UserEndpoint
import net.corda.libs.permissions.manager.PermissionManager
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.permissions.service.PermissionServiceComponent
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class PermissionRpcOpsServiceEventHandlerTest {

    private val permissionServiceComponent = mock<PermissionServiceComponent>()
    private val coordinator = mock<LifecycleCoordinator>()
    private val registration = mock<RegistrationHandle>()
    private val userEndpoint = mock<UserEndpoint>()
    private val permissionManager = mock<PermissionManager>()
    private val permissionValidator = mock<PermissionValidator>()

    private val handler = PermissionRpcOpsServiceEventHandler(permissionServiceComponent, userEndpoint)

    @BeforeEach
    fun setUp() {
        whenever(coordinator.followStatusChangesByName(any())).thenReturn(registration)
        whenever(permissionServiceComponent.permissionManager).thenReturn(permissionManager)
        whenever(permissionServiceComponent.permissionValidator).thenReturn(permissionValidator)
    }

    private fun startAndReceiveStatusUP() {
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
        startAndReceiveStatusUP()

        verify(userEndpoint).permissionManager = permissionManager
        verify(userEndpoint).permissionValidator = permissionValidator
        verify(userEndpoint).start()
        verify(coordinator).updateStatus(LifecycleStatus.UP)
    }


    @Test
    fun `processing a DOWN event sets the coordinator to DOWN and nulls the endpoint permission manager and validator`() {
        startAndReceiveStatusUP()

        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.DOWN), coordinator)

        verify(userEndpoint).permissionValidator = null
        verify(userEndpoint).permissionManager = null
        verify(coordinator).updateStatus(LifecycleStatus.DOWN)
    }

    @Test
    fun `processing a ERROR event from permission service sets the coordinator to ERROR and stops the user endpoint`() {
        startAndReceiveStatusUP()

        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.ERROR), coordinator)

        verify(userEndpoint).stop()
        verify(coordinator).updateStatus(LifecycleStatus.ERROR)
    }

    @Test
    fun `processing a stop event stops the service's dependencies`() {
        assertNull(handler.registration)

        startAndReceiveStatusUP()

        handler.processEvent(StopEvent(), coordinator)

        assertNull(handler.registration)
        verify(userEndpoint).stop()
        verify(coordinator).updateStatus(LifecycleStatus.DOWN)
    }


    @Test
    fun `processing DOWN event from permission service will set validator and manager to null on endpoints`() {
        handler.processEvent(StartEvent(), coordinator)
        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.DOWN), coordinator)

        verify(userEndpoint).permissionManager = null
        verify(userEndpoint).permissionValidator = null
        verify(coordinator).updateStatus(LifecycleStatus.DOWN)
    }

    @Test
    fun `processing ERROR event from permission service will set validator and manager to null on endpoints`() {
        handler.processEvent(StartEvent(), coordinator)
        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.ERROR), coordinator)

        verify(userEndpoint).stop()
        verify(coordinator).updateStatus(LifecycleStatus.ERROR)
    }

}