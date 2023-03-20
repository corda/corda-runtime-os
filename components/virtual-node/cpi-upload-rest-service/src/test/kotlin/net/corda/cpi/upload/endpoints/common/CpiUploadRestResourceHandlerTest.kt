package net.corda.cpi.upload.endpoints.common

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StopEvent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class CpiUploadRestResourceHandlerTest {
    private lateinit var lifecycleHandler: CpiUploadRestResourceHandler

    private lateinit var coordinator: LifecycleCoordinator

    @BeforeEach
    fun setUp() {
        lifecycleHandler = CpiUploadRestResourceHandler()
        coordinator = mock()
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
        lifecycleHandler.processEvent(event, coordinator)
        verify(coordinator).updateStatus(LifecycleStatus.ERROR)
    }

    @Test
    fun `on StopEvent event closes resources and sets coordinator status to DOWN`() {
        lifecycleHandler.processEvent(StopEvent(), coordinator)
        verify(coordinator).updateStatus(LifecycleStatus.DOWN)
    }
}