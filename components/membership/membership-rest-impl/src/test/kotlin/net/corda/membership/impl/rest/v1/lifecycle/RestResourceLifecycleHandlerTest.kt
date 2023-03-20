package net.corda.membership.impl.rest.v1.lifecycle

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.client.MemberResourceClient
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class RestResourceLifecycleHandlerTest {
    private val componentHandle: RegistrationHandle = mock()

    private val coordinator: LifecycleCoordinator = mock {
        on { followStatusChangesByName(any()) } doReturn componentHandle
    }

    private val activate: (String) -> Unit = mock()
    private val deactivate: (String) -> Unit = mock()
    private val registrationRestResourceLifecycleHandler = RestResourceLifecycleHandler(
        activate,
        deactivate,
        setOf(LifecycleCoordinatorName.forComponent<MemberResourceClient>())
    )

    private val registrationHandle: RegistrationHandle = mock()

    @Test
    fun `start event starts following the statuses of the required dependencies`() {
        registrationRestResourceLifecycleHandler.processEvent(StartEvent(), coordinator)
        verify(coordinator).followStatusChangesByName(
            eq(setOf(LifecycleCoordinatorName.forComponent<MemberResourceClient>()))
        )
    }

    @Test
    fun `stop event does not close component handle when there was no start event`() {
        registrationRestResourceLifecycleHandler.processEvent(StopEvent(), coordinator)
        verify(componentHandle, never()).close()
    }

    @Test
    fun `component handle is created after starting and closed when stopping`() {
        registrationRestResourceLifecycleHandler.processEvent(StartEvent(), coordinator)
        registrationRestResourceLifecycleHandler.processEvent(StopEvent(), coordinator)

        verify(componentHandle).close()
    }

    @Test
    fun `registration status UP sets coordinator status to UP`() {
        registrationRestResourceLifecycleHandler.processEvent(
            RegistrationStatusChangeEvent(registrationHandle, LifecycleStatus.UP), coordinator
        )
        verify(activate).invoke(any())
    }

    @Test
    fun `registration status DOWN sets coordinator status to DOWN`() {
        registrationRestResourceLifecycleHandler.processEvent(StartEvent(), coordinator)
        registrationRestResourceLifecycleHandler.processEvent(
            RegistrationStatusChangeEvent(registrationHandle, LifecycleStatus.DOWN), coordinator
        )

        verify(deactivate).invoke(any())
    }

    @Test
    fun `registration status ERROR sets coordinator status to DOWN`() {
        registrationRestResourceLifecycleHandler.processEvent(
            RegistrationStatusChangeEvent(mock(), LifecycleStatus.ERROR), coordinator
        )

        verify(deactivate).invoke(any())
    }
}