package net.corda.components.rpc.internal

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.permissions.rpcops.PermissionRpcOpsService
import net.corda.permissions.service.PermissionServiceComponent
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class RbacPermissionSystemEventHandlerTest {

    private val permissionServiceComponent = mock<PermissionServiceComponent>()
    private val permissionRpcOpsService = mock<PermissionRpcOpsService>()

    private val registrationHandle = mock<RegistrationHandle>()
    private val coordinator = mock<LifecycleCoordinator>()

    private val handler = RbacPermissionSystemEventHandler(permissionServiceComponent, permissionRpcOpsService)

    @BeforeEach
    fun setUp() {
        whenever(coordinator.followStatusChangesByName(any())).thenReturn(registrationHandle)
    }

    @Test
    fun `processing a start event starts the permission service and permission rpcops service`() {
        handler.processEvent(StartEvent(), coordinator)

        verify(permissionServiceComponent).start()
        verify(permissionRpcOpsService).start()
    }

    @Test
    fun `processing a start event causes service to follow status changes for permission service and permission rpcops`() {
        assertNull(handler.registration)

        handler.processEvent(StartEvent(), coordinator)

        assertNotNull(handler.registration)
    }

    @Test
    fun `processing an UP status change sets coordinator status to UP`() {
        handler.processEvent(StartEvent(), coordinator)
        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator)

        verify(coordinator).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `processing a DOWN status change from the child components updates the gateway status`() {
        handler.processEvent(StartEvent(), coordinator)
        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.DOWN), coordinator)

        verify(coordinator).updateStatus(LifecycleStatus.DOWN)
    }

    @Test
    fun `processing a ERROR status change sets coordinator status to ERROR`() {
        handler.processEvent(StartEvent(), coordinator)
        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.ERROR), coordinator)

        verify(coordinator).stop()
        verify(coordinator).updateStatus(LifecycleStatus.ERROR)
    }

    @Test
    fun `processing a STOP event stops the service's dependencies and sets service's status to DOWN`() {
        handler.processEvent(StartEvent(), coordinator)
        handler.processEvent(StopEvent(), coordinator)

        verify(registrationHandle).close()
        verify(permissionServiceComponent).stop()
        verify(permissionRpcOpsService).stop()
        assertNull(handler.registration)
    }
}