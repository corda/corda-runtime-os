package net.corda.membership.impl.registration

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.lib.GroupPolicy
import net.corda.membership.lib.exceptions.RegistrationProtocolSelectionException
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.impl.GroupPolicyExtension
import net.corda.membership.impl.GroupPolicyImpl
import net.corda.membership.registration.MemberRegistrationService
import net.corda.membership.registration.MembershipRequestRegistrationOutcome
import net.corda.membership.registration.MembershipRequestRegistrationResult
import net.corda.virtualnode.HoldingIdentity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RegistrationProxyImplTest {
    companion object {
        val registrationResult1 =
            MembershipRequestRegistrationResult(MembershipRequestRegistrationOutcome.SUBMITTED, "mock1")
        val registrationResult2 =
            MembershipRequestRegistrationResult(MembershipRequestRegistrationOutcome.SUBMITTED, "mock2")
    }

    private val registrationProtocol1 = RegistrationProtocol1()
    private val registrationProtocol2 = RegistrationProtocol2()
    private val registrationProtocols = listOf(
        registrationProtocol1,
        registrationProtocol2,
    )
    var handler: LifecycleEventHandler? = null
    private val registrationStatusHandle: RegistrationHandle = mock()
    var coordinatorIsRunning = false
    var coordinatorStatus = LifecycleStatus.DOWN
    private val coordinator: LifecycleCoordinator = mock {
        on { followStatusChangesByName(any()) } doReturn registrationStatusHandle
        on { start() } doAnswer {
            coordinatorIsRunning = true
            handler?.processEvent(StartEvent(), mock)
        }
        on { stop() } doAnswer {
            coordinatorIsRunning = false
            handler?.processEvent(StopEvent(), mock)
        }
        on { isRunning } doAnswer { coordinatorIsRunning }
        on { updateStatus(any(), any()) } doAnswer { coordinatorStatus = it.arguments[0] as LifecycleStatus }
        on { status } doAnswer { coordinatorStatus }
    }
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), any()) } doAnswer {
            handler = it.arguments[1] as LifecycleEventHandler
            coordinator
        }
    }

    private val groupPolicyProvider = mock<GroupPolicyProvider> {
        on { getGroupPolicy(any()) } doReturn mock()
    }

    private fun createHoldingIdentity() = HoldingIdentity("O=Alice, L=London, C=GB", "ABC")
    private fun createGroupPolicy(registrationProtocol: String) = GroupPolicyImpl(
        mapOf(GroupPolicyExtension.REGISTRATION_PROTOCOL_KEY to registrationProtocol)
    )

    private fun mockGroupPolicy(groupPolicy: GroupPolicy, holdingIdentity: HoldingIdentity) =
        doReturn(groupPolicy).whenever(groupPolicyProvider).getGroupPolicy(holdingIdentity)

    private lateinit var registrationProxy: RegistrationProxyImpl

    @BeforeEach
    fun setUp() {
        registrationProxy = RegistrationProxyImpl(
            lifecycleCoordinatorFactory,
            groupPolicyProvider,
            registrationProtocols
        )
        registrationProtocols.forEach { it.started = 0 }
    }

    private fun registrationChange(status: LifecycleStatus = LifecycleStatus.UP) {
        handler?.processEvent(RegistrationStatusChangeEvent(mock(), status), coordinator)
    }

    private fun startComponentAndDependencies() {
        groupPolicyProvider.start()
        registrationChange()
    }

    @Test
    fun `Proxy selects correct registration protocol for calling registration`() {
        startComponentAndDependencies()
        val identity1 = createHoldingIdentity()
        mockGroupPolicy(createGroupPolicy(RegistrationProtocol1::class.java.name), identity1)
        assertEquals(registrationResult1, registrationProxy.register(identity1))

        val identity2 = createHoldingIdentity()
        mockGroupPolicy(createGroupPolicy(RegistrationProtocol2::class.java.name), identity2)
        assertEquals(registrationResult2, registrationProxy.register(identity2))
    }

    @Test
    fun `Proxy throws exception for invalid registration protocol config`() {
        startComponentAndDependencies()
        val identity = createHoldingIdentity()
        mockGroupPolicy(createGroupPolicy(String::class.java.name), identity)
        assertThrows<RegistrationProtocolSelectionException> {
            registrationProxy.register(identity)
        }
    }

    @Test
    fun `Start calls start on coordinator`() {
        registrationProxy.start()
        verify(coordinator).start()
    }

    @Test
    fun `Stop calls stop on coordinator`() {
        registrationProxy.stop()
        verify(coordinator).stop()
    }

    @Test
    fun `Service API fails when service is not running`() {
        doReturn(false).whenever(coordinator).isRunning
        val identity = createHoldingIdentity()
        mockGroupPolicy(createGroupPolicy(RegistrationProtocol1::class.java.name), identity)
        assertThrows<IllegalStateException> { registrationProxy.register(identity) }
    }

    @Test
    fun `Service API fails when coordinator status is DOWN`() {
        doReturn(true).whenever(coordinator).isRunning
        doReturn(LifecycleStatus.DOWN).whenever(coordinator).status
        val identity = createHoldingIdentity()
        mockGroupPolicy(createGroupPolicy(RegistrationProtocol1::class.java.name), identity)
        assertThrows<IllegalStateException> { registrationProxy.register(identity) }
    }

    @Test
    fun `Service API fails when coordinator is ERROR`() {
        doReturn(true).whenever(coordinator).isRunning
        doReturn(LifecycleStatus.ERROR).whenever(coordinator).status
        val identity = createHoldingIdentity()
        mockGroupPolicy(createGroupPolicy(RegistrationProtocol1::class.java.name), identity)
        assertThrows<IllegalStateException> { registrationProxy.register(identity) }
    }

    @Test
    fun `start event starts registration protocols and follows statuses of dependencies`() {
        handler?.processEvent(StartEvent(), coordinator)

        verify(coordinator).followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<GroupPolicyProvider>(),
                registrationProtocol1.lifecycleCoordinatorName,
                registrationProtocol2.lifecycleCoordinatorName,
            )
        )
        verify(registrationStatusHandle, never()).close()
        assertEquals(1, registrationProtocol1.started)
        assertEquals(1, registrationProtocol2.started)

        verify(coordinator, never()).updateStatus(any(), any())
    }

    @Test
    fun `start event called a second time closes previously created registration handle`() {
        handler?.processEvent(StartEvent(), coordinator)
        handler?.processEvent(StartEvent(), coordinator)

        verify(coordinator, times(2)).followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<GroupPolicyProvider>(),
                registrationProtocol1.lifecycleCoordinatorName,
                registrationProtocol2.lifecycleCoordinatorName,
            )
        )
        verify(registrationStatusHandle).close()
        assertEquals(2, registrationProtocol1.started)
        assertEquals(2, registrationProtocol2.started)
        verify(coordinator, never()).updateStatus(any(), any())
    }

    @Test
    fun `stop event before start event doesn't close registration handle and sets status to down`() {
        handler?.processEvent(StopEvent(), coordinator)

        verify(registrationStatusHandle, never()).close()
        assertEquals(coordinatorStatus, LifecycleStatus.DOWN)
    }

    @Test
    fun `stop event after start event closes registration handle and sets status to down`() {
        handler?.processEvent(StartEvent(), coordinator)
        handler?.processEvent(StopEvent(), coordinator)

        verify(registrationStatusHandle).close()
        assertEquals(coordinatorStatus, LifecycleStatus.DOWN)
    }

    @Test
    fun `Registration changed event DOWN sets coordinator status DOWN`() {
        registrationChange(LifecycleStatus.DOWN)

        assertEquals(coordinatorStatus, LifecycleStatus.DOWN)
    }

    @Test
    fun `Registration changed event UP sets coordinator status UP`() {
        registrationChange()

        assertEquals(coordinatorStatus, LifecycleStatus.UP)
    }

    class RegistrationProtocol1 : AbstractRegistrationProtocol() {
        override fun register(member: HoldingIdentity): MembershipRequestRegistrationResult = registrationResult1
    }

    class RegistrationProtocol2 : AbstractRegistrationProtocol() {
        override fun register(member: HoldingIdentity): MembershipRequestRegistrationResult = registrationResult2
    }

    abstract class AbstractRegistrationProtocol : MemberRegistrationService {
        var started = 0
        override fun register(member: HoldingIdentity) =
            MembershipRequestRegistrationResult(MembershipRequestRegistrationOutcome.SUBMITTED, "mock")

        override val isRunning = true
        override fun start() { started += 1 }
        override fun stop() {}
    }
}
