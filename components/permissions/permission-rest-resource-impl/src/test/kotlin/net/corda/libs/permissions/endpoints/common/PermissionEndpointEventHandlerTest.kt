package net.corda.libs.permissions.endpoints.common

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.permissions.management.PermissionManagementService
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class PermissionEndpointEventHandlerTest {

    private val coordinator = mock<LifecycleCoordinator>()
    private val registrationHandle = mock<RegistrationHandle>()

    private val handler = PermissionEndpointEventHandler("test")

    @BeforeEach
    fun setUp() {
        whenever(coordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<PermissionManagementService>()
            )
        )).thenReturn(registrationHandle)
    }

    @Test
    fun `processing a start event follows permission management service for status updates`() {
        assertNull(handler.registration)

        handler.processEvent(StartEvent(), coordinator)

        verify(coordinator).followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<PermissionManagementService>()
            )
        )
        assertNotNull(handler.registration)
    }

    @Test
    fun `processing UP status update sets coordinator status to UP`() {
        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator)

        verify(coordinator).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `processing DOWN status update sets coordinator status to DOWN`() {
        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.DOWN), coordinator)

        verify(coordinator).updateStatus(LifecycleStatus.DOWN)
    }

}