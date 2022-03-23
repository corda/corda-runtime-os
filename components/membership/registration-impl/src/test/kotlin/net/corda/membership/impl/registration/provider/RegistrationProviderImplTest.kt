package net.corda.membership.impl.registration.provider

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.membership.GroupPolicy
import net.corda.membership.exceptions.RegistrationProtocolSelectionException
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.impl.GroupPolicyExtension.Companion.REGISTRATION_PROTOCOL_KEY
import net.corda.membership.impl.GroupPolicyImpl
import net.corda.membership.registration.MemberRegistrationService
import net.corda.membership.registration.MembershipRequestRegistrationResult
import net.corda.membership.registration.provider.RegistrationProvider
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.HoldingIdentity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RegistrationProviderImplTest {
    companion object {
        val registrationProtocol1 = RegistrationProtocol1()
        val registrationProtocol2 = RegistrationProtocol2()
        val registrationProtocol3 = RegistrationProtocol3()

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

    fun createHoldingIdentity() = HoldingIdentity("O=Alice, L=London, C=GB", "ABC")
    fun createGroupPolicy(registrationProtocol: String) = GroupPolicyImpl(
        mapOf(REGISTRATION_PROTOCOL_KEY to registrationProtocol)
    )

    fun mockGroupPolicy(groupPolicy: GroupPolicy, holdingIdentity: HoldingIdentity) =
        doReturn(groupPolicy).whenever(groupPolicyProvider).getGroupPolicy(holdingIdentity)

    private lateinit var registrationProvider: RegistrationProvider

    @BeforeEach
    fun setUp() {
        registrationProvider = RegistrationProviderImpl(
            lifecycleCoordinatorFactory,
            groupPolicyProvider,
            registrationProtocols
        )
    }

    fun setCoordinatorToRunningAndUpStatus() {
        doReturn(true).whenever(coordinator).isRunning
        doReturn(LifecycleStatus.UP).whenever(coordinator).status
    }

    @Test
    fun `Provider selects correct registration protocol`() {
        setCoordinatorToRunningAndUpStatus()
        val identity1 = createHoldingIdentity()
        mockGroupPolicy(createGroupPolicy(RegistrationProtocol1::class.java.name), identity1)
        assertEquals(registrationProtocol1, registrationProvider.get(identity1))

        val identity2 = createHoldingIdentity()
        mockGroupPolicy(createGroupPolicy(RegistrationProtocol2::class.java.name), identity2)
        assertEquals(registrationProtocol2, registrationProvider.get(identity2))
    }

    @Test
    fun `Provider throws exception for invalid registration protocol config`() {
        setCoordinatorToRunningAndUpStatus()
        val identity = createHoldingIdentity()
        mockGroupPolicy(createGroupPolicy(String::class.java.name), identity)
        assertThrows<RegistrationProtocolSelectionException> {
            registrationProvider.get(identity)
        }
    }

    @Test
    fun `Start calls start on coordinator`() {
        registrationProvider.start()
        verify(coordinator).start()
    }

    @Test
    fun `Stop calls stop on coordinator`() {
        registrationProvider.stop()
        verify(coordinator).stop()
    }

    @Test
    fun `Service API fails when service is not running`() {
        doReturn(false).whenever(coordinator).isRunning
        val identity = createHoldingIdentity()
        mockGroupPolicy(createGroupPolicy(RegistrationProtocol1::class.java.name), identity)
        assertThrows<CordaRuntimeException> { registrationProvider.get(identity) }
    }

    @Test
    fun `Service API fails when coordinator status is DOWN`() {
        doReturn(true).whenever(coordinator).isRunning
        doReturn(LifecycleStatus.DOWN).whenever(coordinator).status
        val identity = createHoldingIdentity()
        mockGroupPolicy(createGroupPolicy(RegistrationProtocol1::class.java.name), identity)
        assertThrows<CordaRuntimeException> { registrationProvider.get(identity) }
    }

    @Test
    fun `Service API fails when coordinator is ERROR`() {
        doReturn(true).whenever(coordinator).isRunning
        doReturn(LifecycleStatus.ERROR).whenever(coordinator).status
        val identity = createHoldingIdentity()
        mockGroupPolicy(createGroupPolicy(RegistrationProtocol1::class.java.name), identity)
        assertThrows<CordaRuntimeException> { registrationProvider.get(identity) }
    }

    class RegistrationProtocol1 : AbstractRegistrationProtocol()
    class RegistrationProtocol2 : AbstractRegistrationProtocol()
    class RegistrationProtocol3 : AbstractRegistrationProtocol()
    abstract class AbstractRegistrationProtocol : MemberRegistrationService {
        override fun register(member: HoldingIdentity) = mock<MembershipRequestRegistrationResult>()
        override val isRunning = true
        override fun start() {}
        override fun stop() {}
    }
}