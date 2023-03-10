package net.corda.cpi.upload.endpoints.common

import net.corda.cpi.upload.endpoints.service.CpiUploadService
import net.corda.cpiinfo.read.CpiInfoReadService
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

class CpiUploadRestResourceHandlerTest {
    private lateinit var lifecycleHandler: CpiUploadRestResourceHandler

    private lateinit var coordinator: LifecycleCoordinator

    @BeforeEach
    fun setUp() {
        lifecycleHandler = CpiUploadRestResourceHandler()
        coordinator = mock()
    }

    @Test
    fun `on Start event follows CpiUploadService for changes`() {
        val registrationHandle = mock<RegistrationHandle>()
        whenever(coordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<CpiUploadService>(),
                LifecycleCoordinatorName.forComponent<CpiInfoReadService>()
            )
        )).thenReturn(registrationHandle)

        assertNull(lifecycleHandler.registrationHandle)
        lifecycleHandler.processEvent(StartEvent(), coordinator)
        assertNotNull(lifecycleHandler.registrationHandle)
    }

    @Test
    fun `on RegistrationStatusChangeEvent UP event updates coordinator status to UP`() {
        val event = RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP)
        lifecycleHandler.processEvent(event, coordinator)
        verify(coordinator).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `on RegistrationStatusChangeEvent ERROR event closes resources and updates coordinator status to ERROR`(){
        val event = RegistrationStatusChangeEvent(mock(), LifecycleStatus.ERROR)
        val registrationHandle = mock<RegistrationHandle>()
        lifecycleHandler.registrationHandle = registrationHandle
        lifecycleHandler.processEvent(event, coordinator)
        verify(registrationHandle).close()
        assertNull(lifecycleHandler.registrationHandle)
        verify(coordinator).updateStatus(LifecycleStatus.ERROR)
    }

    @Test
    fun `on StopEvent event closes resources and sets coordinator status to DOWN`() {
        val registrationHandle = mock<RegistrationHandle>()
        lifecycleHandler.registrationHandle = registrationHandle
        lifecycleHandler.processEvent(StopEvent(), coordinator)
        verify(registrationHandle).close()
        assertNull(lifecycleHandler.registrationHandle)
        verify(coordinator).updateStatus(LifecycleStatus.DOWN)
    }
}