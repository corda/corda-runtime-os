package net.corda.membership.synchronisation

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.data.membership.MembershipPackage
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.lib.exceptions.SynchronisationProtocolSelectionException
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.PROTOCOL_MODE
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.SESSION_PKI
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.TLS_PKI
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.TLS_TRUST_ROOTS
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.TLS_VERSION
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.ProtocolParameters.SESSION_KEY_POLICY
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.CIPHER_SUITE
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.FILE_FORMAT_VERSION
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.GROUP_ID
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.P2P_PARAMETERS
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.PROTOCOL_PARAMETERS
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.REGISTRATION_PROTOCOL
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.SYNC_PROTOCOL
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.ProtocolMode.AUTH_ENCRYPT
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode.NO_PKI
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsPkiMode.STANDARD
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsVersion.VERSION_1_3
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.ProtocolParameters.SessionKeyPolicy.COMBINED
import net.corda.membership.lib.impl.grouppolicy.v1.MemberGroupPolicyImpl
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertFailsWith

class SynchronisationProxyImplTest {
    private companion object {
        const val DUMMY_GROUP_ID = "dummy_group"
    }

    private val syncProtocol1 = SyncProtocol1()
    private val syncProtocol2 = SyncProtocol2()
    private val syncProtocols = listOf(
        syncProtocol1,
        syncProtocol2,
    )
    private var handler: LifecycleEventHandler? = null
    private val registrationStatusHandle: RegistrationHandle = mock()
    private var coordinatorIsRunning = false
    private var coordinatorStatus = LifecycleStatus.DOWN
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
    private lateinit var synchronisationProxy: SynchronisationProxy

    private fun createHoldingIdentity() = HoldingIdentity("O=Alice, L=London, C=GB", DUMMY_GROUP_ID)

    private fun createGroupPolicy(synchronisationProtocol: String): GroupPolicy {
        return MemberGroupPolicyImpl(
            ObjectMapper().readTree(
                """
                {
                    "$FILE_FORMAT_VERSION": 1,
                    "$GROUP_ID": "$DUMMY_GROUP_ID",
                    "$REGISTRATION_PROTOCOL": "com.foo.bar.RegProtocol",
                    "$SYNC_PROTOCOL": "$synchronisationProtocol",
                    "$PROTOCOL_PARAMETERS": {
                        "$SESSION_KEY_POLICY": "$COMBINED"
                    },
                    "$P2P_PARAMETERS": {
                        "$SESSION_PKI": "$NO_PKI",
                        "$TLS_TRUST_ROOTS": [
                          "${TestUtils.r3comCert}"
                        ],
                        "$TLS_PKI": "$STANDARD",
                        "$TLS_VERSION": "$VERSION_1_3",
                        "$PROTOCOL_MODE": "$AUTH_ENCRYPT"
                    },
                    "$CIPHER_SUITE": {}
                }
            """.trimIndent()
            )
        )
    }

    private fun mockGroupPolicy(groupPolicy: GroupPolicy, holdingIdentity: HoldingIdentity) =
        doReturn(groupPolicy).whenever(groupPolicyProvider).getGroupPolicy(holdingIdentity)

    @BeforeEach
    fun setUp() {
        synchronisationProxy = SynchronisationProxyImpl(
            lifecycleCoordinatorFactory,
            mock(),
            mock(),
            mock(),
            groupPolicyProvider,
            syncProtocols
        )
        syncProtocols.forEach { it.started = 0 }
    }

    private fun registrationChange(status: LifecycleStatus = LifecycleStatus.UP) {
        handler?.processEvent(RegistrationStatusChangeEvent(mock(), status), coordinator)
    }

    private fun startComponentAndDependencies() {
        groupPolicyProvider.start()
        registrationChange()
    }

    @Test
    fun `Proxy selects correct synchronisation protocol`() {
        startComponentAndDependencies()
        val identity1 = createHoldingIdentity()
        mockGroupPolicy(createGroupPolicy(SyncProtocol1::class.java.name), identity1)
        val ex1 = assertFailsWith<SynchronisationException> {
            synchronisationProxy.processMembershipUpdates(identity1, mock())
        }
        assertThat(ex1.message).isEqualTo("SyncProtocol1 called")

        val identity2 = createHoldingIdentity()
        mockGroupPolicy(createGroupPolicy(SyncProtocol2::class.java.name), identity2)
        val ex2 = assertFailsWith<SynchronisationException> {
            synchronisationProxy.processMembershipUpdates(identity2, mock())
        }
        assertThat(ex2.message).isEqualTo("SyncProtocol2 called")
    }

    @Test
    fun `Proxy throws exception for invalid synchronisation protocol config`() {
        startComponentAndDependencies()
        val identity = createHoldingIdentity()
        mockGroupPolicy(createGroupPolicy(String::class.java.name), identity)
        assertFailsWith<SynchronisationProtocolSelectionException> {
            synchronisationProxy.processMembershipUpdates(identity, mock())
        }
    }

    @Test
    fun `Start calls start on coordinator`() {
        synchronisationProxy.start()
        verify(coordinator).start()
    }

    @Test
    fun `Stop calls stop on coordinator`() {
        synchronisationProxy.stop()
        verify(coordinator).stop()
    }

    @Test
    fun `Service API fails when service is not running`() {
        doReturn(false).whenever(coordinator).isRunning
        val identity = createHoldingIdentity()
        mockGroupPolicy(createGroupPolicy(SyncProtocol1::class.java.name), identity)
        assertFailsWith<IllegalStateException> { synchronisationProxy.processMembershipUpdates(identity, mock()) }
    }

    class SyncProtocol1 : AbstractSyncProtocol() {
        override fun processMembershipUpdates(member: HoldingIdentity, membershipPackage: MembershipPackage) =
            throw SynchronisationException("SyncProtocol1 called")
    }

    class SyncProtocol2 : AbstractSyncProtocol() {
        override fun processMembershipUpdates(member: HoldingIdentity, membershipPackage: MembershipPackage) =
            throw SynchronisationException("SyncProtocol2 called")
    }

    abstract class AbstractSyncProtocol : MemberSynchronisationService {
        var started = 0

        override fun processMembershipUpdates(member: HoldingIdentity, membershipPackage: MembershipPackage) =
            throw SynchronisationException("AbstractSyncProtocol called")

        override val isRunning = true
        override fun start() { started += 1 }
        override fun stop() {}
    }
}
