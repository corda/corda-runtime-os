package net.corda.cpi.upload.endpoints.common

import net.corda.cpi.upload.endpoints.service.CpiUploadRPCOpsService
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StopEvent
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CpiUploadRPCOpsHandlerTest {
    private lateinit var rpcOpsHandler: CpiUploadRPCOpsHandler

    private lateinit var coordinator: LifecycleCoordinator

    @BeforeEach
    fun setUp() {
        rpcOpsHandler = CpiUploadRPCOpsHandler()
        coordinator = mock()
    }

    @Test
    fun `on Start event follow CpiUploadRPCOpsService for changes`() {
        val registrationHandle = mock<RegistrationHandle>()
        whenever(coordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<CpiUploadRPCOpsService>()
            )
        )).thenReturn(registrationHandle)

        assertNull(rpcOpsHandler.cpiUploadRPCOpsServiceRegistrationHandle)
        rpcOpsHandler.processEvent(StartEvent(), coordinator)
        assertNotNull(rpcOpsHandler.cpiUploadRPCOpsServiceRegistrationHandle)
    }

    @Test
    fun `on RegistrationStatusChangeEvent UP event update coordinator status to UP`() {
        val event = RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP)
        rpcOpsHandler.processEvent(event, coordinator)
        verify(coordinator).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `on RegistrationStatusChangeEvent ERROR event close resources and update coordinator status to ERROR`(){
        val event = RegistrationStatusChangeEvent(mock(), LifecycleStatus.ERROR)
        val registrationHandle = mock<RegistrationHandle>()
        rpcOpsHandler.cpiUploadRPCOpsServiceRegistrationHandle = registrationHandle
        rpcOpsHandler.processEvent(event, coordinator)
        verify(coordinator).updateStatus(LifecycleStatus.ERROR)
        verify(registrationHandle).close()
        assertNull(rpcOpsHandler.cpiUploadRPCOpsServiceRegistrationHandle)
    }

    @Test
    fun `on StopEvent event close resources and set coordinator status to DOWN`() {
        val registrationHandle = mock<RegistrationHandle>()
        rpcOpsHandler.cpiUploadRPCOpsServiceRegistrationHandle = registrationHandle
        rpcOpsHandler.processEvent(StopEvent(), coordinator)
        verify(coordinator).updateStatus(LifecycleStatus.DOWN)
        verify(registrationHandle).close()
        assertNull(rpcOpsHandler.cpiUploadRPCOpsServiceRegistrationHandle)
    }
}