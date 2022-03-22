package net.corda.membership.impl.registration.proxy.lifecycle

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.registration.MemberRegistrationService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class RegistrationProxyLifecycleHandlerTest {
    lateinit var registrationProxyLifecycleHandler: RegistrationProxyLifecycleHandler

    interface RegistrationService1 : MemberRegistrationService
    interface RegistrationService2 : MemberRegistrationService
    private val registrationProtocol1 = mock<RegistrationService1>()
    private val registrationProtocol2 = mock<RegistrationService2>()
    private val registrationProtocols = listOf(registrationProtocol1, registrationProtocol2)

    private val registrationStatusHandle: RegistrationHandle = mock()
    private val coordinator: LifecycleCoordinator = mock {
        on { followStatusChangesByName(any()) } doReturn registrationStatusHandle
    }

    private val activate: (String) -> Unit = mock()
    private val deactivate: (String) -> Unit = mock()

    @BeforeEach
    fun setUp() {
        registrationProxyLifecycleHandler = RegistrationProxyLifecycleHandler(registrationProtocols, activate, deactivate)
    }

    @Test
    fun `start event starts registration protocols and follows statuses of dependencies`() {
        registrationProxyLifecycleHandler.processEvent(StartEvent(), coordinator)

        verify(coordinator).followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<GroupPolicyProvider>(),
                registrationProtocol1.lifecycleCoordinatorName,
                registrationProtocol2.lifecycleCoordinatorName
            )
        )
        verify(registrationStatusHandle, never()).close()
        verify(registrationProtocol1).start()
        verify(registrationProtocol2).start()

        verify(coordinator, never()).updateStatus(any(), any())
    }

    @Test
    fun `start event called a second time closes previously created registration handle`() {
        registrationProxyLifecycleHandler.processEvent(StartEvent(), coordinator)
        registrationProxyLifecycleHandler.processEvent(StartEvent(), coordinator)

        verify(coordinator, times(2)).followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<GroupPolicyProvider>(),
                registrationProtocol1.lifecycleCoordinatorName,
                registrationProtocol2.lifecycleCoordinatorName
            )
        )
        verify(registrationStatusHandle).close()
        verify(registrationProtocol1, times(2)).start()
        verify(registrationProtocol2, times(2)).start()
        verify(coordinator, never()).updateStatus(any(), any())
    }

    @Test
    fun `stop event before start event doesn't close registration handle and sets status to down`() {
        registrationProxyLifecycleHandler.processEvent(StopEvent(), coordinator)

        verify(registrationStatusHandle, never()).close()
        verify(deactivate).invoke(any())
    }

    @Test
    fun `stop event after start event closes registration handle and sets status to down`() {
        registrationProxyLifecycleHandler.processEvent(StartEvent(), coordinator)
        registrationProxyLifecycleHandler.processEvent(StopEvent(), coordinator)

        verify(registrationStatusHandle).close()
        verify(deactivate).invoke(any())
    }

    @Test
    fun `Registration changed event DOWN sets coordinator status DOWN`() {
        registrationProxyLifecycleHandler.processEvent(
            RegistrationStatusChangeEvent(registrationStatusHandle, LifecycleStatus.DOWN),
            coordinator
        )

        verify(deactivate).invoke(any())
    }

    @Test
    fun `Registration changed event UP sets coordinator status UP`() {
        registrationProxyLifecycleHandler.processEvent(
            RegistrationStatusChangeEvent(registrationStatusHandle, LifecycleStatus.UP),
            coordinator
        )

        verify(activate).invoke(any())
    }
}
