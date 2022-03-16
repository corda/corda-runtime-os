package net.corda.membership.impl.registration.proxy

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.membership.GroupPolicy
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.impl.GroupPolicyExtension
import net.corda.membership.impl.GroupPolicyImpl
import net.corda.membership.registration.MemberRegistrationService
import net.corda.membership.registration.MembershipRequestRegistrationOutcome
import net.corda.membership.registration.MembershipRequestRegistrationResult
import net.corda.membership.registration.proxy.RegistrationProxy
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.HoldingIdentity
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RegistrationProxyImplTest {
    companion object {
        val registrationResult1 =
            MembershipRequestRegistrationResult(MembershipRequestRegistrationOutcome.SUBMITTED, "mock1")
        val registrationResult2 =
            MembershipRequestRegistrationResult(MembershipRequestRegistrationOutcome.SUBMITTED, "mock2")
        val registrationResult3 =
            MembershipRequestRegistrationResult(MembershipRequestRegistrationOutcome.SUBMITTED, "mock3")

        private val registrationProtocol1 = RegistrationProtocol1()
        private val registrationProtocol2 = RegistrationProtocol2()
        private val registrationProtocol3 = RegistrationProtocol3()

        val registrationProtocols = listOf(
            registrationProtocol1,
            registrationProtocol2,
            registrationProtocol3
        )
    }

    private val coordinator: LifecycleCoordinator = mock()
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), any()) } doReturn coordinator
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

    private lateinit var registrationProxy: RegistrationProxy

    @BeforeEach
    fun setUp() {
        registrationProxy = RegistrationProxyImpl(
            lifecycleCoordinatorFactory,
            groupPolicyProvider,
            registrationProtocols
        )
    }

    private fun setCoordinatorToRunningAndUpStatus() {
        doReturn(true).whenever(coordinator).isRunning
        doReturn(LifecycleStatus.UP).whenever(coordinator).status
    }

    @Test
    fun `Proxy selects correct registration protocol for calling registration`() {
        setCoordinatorToRunningAndUpStatus()
        val identity1 = createHoldingIdentity()
        mockGroupPolicy(createGroupPolicy(RegistrationProtocol1::class.java.name), identity1)
        Assertions.assertEquals(registrationResult1, registrationProxy.register(identity1))

        val identity2 = createHoldingIdentity()
        mockGroupPolicy(createGroupPolicy(RegistrationProtocol2::class.java.name), identity2)
        Assertions.assertEquals(registrationResult2, registrationProxy.register(identity2))
    }

    @Test
    fun `Proxy throws exception for invalid registration protocol config`() {
        setCoordinatorToRunningAndUpStatus()
        val identity = createHoldingIdentity()
        mockGroupPolicy(createGroupPolicy(String::class.java.name), identity)
        assertThrows<CordaRuntimeException> {
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
        assertThrows<CordaRuntimeException> { registrationProxy.register(identity) }
    }

    @Test
    fun `Service API fails when coordinator status is DOWN`() {
        doReturn(true).whenever(coordinator).isRunning
        doReturn(LifecycleStatus.DOWN).whenever(coordinator).status
        val identity = createHoldingIdentity()
        mockGroupPolicy(createGroupPolicy(RegistrationProtocol1::class.java.name), identity)
        assertThrows<CordaRuntimeException> { registrationProxy.register(identity) }
    }

    @Test
    fun `Service API fails when coordinator is ERROR`() {
        doReturn(true).whenever(coordinator).isRunning
        doReturn(LifecycleStatus.ERROR).whenever(coordinator).status
        val identity = createHoldingIdentity()
        mockGroupPolicy(createGroupPolicy(RegistrationProtocol1::class.java.name), identity)
        assertThrows<CordaRuntimeException> { registrationProxy.register(identity) }
    }

    class RegistrationProtocol1 : AbstractRegistrationProtocol() {
        override fun register(member: HoldingIdentity): MembershipRequestRegistrationResult = registrationResult1
    }

    class RegistrationProtocol2 : AbstractRegistrationProtocol() {
        override fun register(member: HoldingIdentity): MembershipRequestRegistrationResult = registrationResult2
    }

    class RegistrationProtocol3 : AbstractRegistrationProtocol() {
        override fun register(member: HoldingIdentity): MembershipRequestRegistrationResult = registrationResult3
    }

    abstract class AbstractRegistrationProtocol : MemberRegistrationService {
        override fun register(member: HoldingIdentity) =
            MembershipRequestRegistrationResult(MembershipRequestRegistrationOutcome.SUBMITTED, "mock")

        override val isRunning = true
        override fun start() {}
        override fun stop() {}
    }
}
