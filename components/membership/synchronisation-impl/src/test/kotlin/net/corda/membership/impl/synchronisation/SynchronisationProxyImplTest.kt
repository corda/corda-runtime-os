package net.corda.membership.impl.synchronisation

import com.fasterxml.jackson.databind.ObjectMapper
import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.membership.command.synchronisation.SynchronisationCommand
import net.corda.data.membership.command.synchronisation.SynchronisationMetaData
import net.corda.data.membership.command.synchronisation.member.ProcessMembershipUpdates
import net.corda.data.membership.command.synchronisation.mgm.ProcessSyncRequest
import net.corda.data.membership.p2p.MembershipPackage
import net.corda.data.membership.p2p.MembershipSyncRequest
import net.corda.libs.configuration.SmartConfigFactoryFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.Resource
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.lib.exceptions.BadGroupPolicyException
import net.corda.membership.lib.exceptions.SynchronisationProtocolSelectionException
import net.corda.membership.lib.exceptions.SynchronisationProtocolTypeException
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.PROTOCOL_MODE
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.SESSION_PKI
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.TLS_PKI
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.TLS_TRUST_ROOTS
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.P2PParameters.TLS_TYPE
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
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsType.ONE_WAY
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsVersion.VERSION_1_3
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.ProtocolParameters.SessionKeyPolicy.COMBINED
import net.corda.membership.lib.impl.grouppolicy.v1.MemberGroupPolicyImpl
import net.corda.membership.synchronisation.MemberSynchronisationService
import net.corda.membership.synchronisation.MgmSynchronisationService
import net.corda.membership.synchronisation.SynchronisationService
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.configuration.ConfigKeys
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.apache.commons.text.StringEscapeUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertFailsWith

class SynchronisationProxyImplTest {
    private companion object {
        const val DUMMY_GROUP_ID = "dummy_group"
        private val classes = mutableListOf<Class<*>>()
    }

    private val memberSyncProtocol1 = MemberSyncProtocol1()
    private val memberSyncProtocol2 = MemberSyncProtocol2()
    private val mgmSyncProtocol1 = MgmSyncProtocol1()
    private val memberSyncProtocols = listOf(
        memberSyncProtocol1,
        memberSyncProtocol2,
    )
    private val mgmSyncProtocols = listOf(
        mgmSyncProtocol1,
    )
    private val syncProtocol1 = AbstractSyncProtocol()
    private val syncProtocols = listOf(syncProtocol1)
    private val dependencyHandle: RegistrationHandle = mock()
    private val subHandle: RegistrationHandle = mock()
    private val configHandle: Resource = mock()
    private val testConfig =
        SmartConfigFactoryFactory.createWithoutSecurityServices().create(ConfigFactory.parseString("instanceId=1"))

    private var coordinatorIsRunning = false
    private var coordinatorStatus: KArgumentCaptor<LifecycleStatus> = argumentCaptor()
    private val dependencies = setOf(
        LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
        LifecycleCoordinatorName.forComponent<GroupPolicyProvider>(),
        memberSyncProtocol1.lifecycleCoordinatorName,
        memberSyncProtocol2.lifecycleCoordinatorName,
        mgmSyncProtocol1.lifecycleCoordinatorName,
        syncProtocol1.lifecycleCoordinatorName,
    )
    private val subscriptionCoordinatorName = LifecycleCoordinatorName("SUB")
    private val subscription: Subscription<String, SynchronisationCommand> = mock {
        on { subscriptionName } doReturn subscriptionCoordinatorName
    }
    private val coordinator: LifecycleCoordinator = mock {
        on { followStatusChangesByName(eq(dependencies)) } doReturn dependencyHandle
        on { followStatusChangesByName(eq(setOf(subscriptionCoordinatorName))) } doReturn subHandle
        on { isRunning } doAnswer { coordinatorIsRunning }
        on { start() } doAnswer {
            coordinatorIsRunning = true
            lifecycleHandlerCaptor.firstValue.processEvent(StartEvent(), mock)
        }
        on { stop() } doAnswer {
            coordinatorIsRunning = false
            lifecycleHandlerCaptor.firstValue.processEvent(StopEvent(), mock)
        }
        doNothing().whenever(it).updateStatus(coordinatorStatus.capture(), any())
        on { status } doAnswer { coordinatorStatus.firstValue }
    }

    private val lifecycleHandlerCaptor: KArgumentCaptor<LifecycleEventHandler> = argumentCaptor()
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), lifecycleHandlerCaptor.capture()) } doReturn coordinator
    }
    private val configReadService: ConfigurationReadService = mock {
        on { registerComponentForUpdates(eq(coordinator), any()) } doReturn configHandle
    }
    private val processor = argumentCaptor<SynchronisationProxyImpl.Processor>()
    private val subscriptionFactory: SubscriptionFactory = mock {
        on {
            createDurableSubscription(
                any(),
                processor.capture(),
                any(),
                eq(null)
            )
        } doReturn subscription
    }
    private val groupPolicyProvider = mock<GroupPolicyProvider> {
        on { getGroupPolicy(any()) } doReturn mock()
    }
    private val membershipPackage: MembershipPackage = mock()
    private val synchronisationMetadata: SynchronisationMetaData = mock {
        on { mgm } doReturn createHoldingIdentity().toAvro()
        on { member } doReturn createHoldingIdentity().toAvro()
    }
    private val updates: ProcessMembershipUpdates = mock {
        on { synchronisationMetaData } doReturn synchronisationMetadata
        on { membershipPackage } doReturn membershipPackage
    }
    private val syncRequest: MembershipSyncRequest = mock()
    private val request: ProcessSyncRequest = mock {
        on { synchronisationMetaData } doReturn synchronisationMetadata
        on { syncRequest } doReturn syncRequest
    }
    private val processMembershipUpdatesRecord = listOf(
        Record("sync_topic", "test_key", SynchronisationCommand(updates))
    )
    private val processSyncRequestRecord = listOf(
        Record("sync_topic", "test_key", SynchronisationCommand(request))
    )

    private lateinit var synchronisationProxy: SynchronisationProxyImpl

    private fun createHoldingIdentity() = createTestHoldingIdentity("O=Alice, L=London, C=GB", DUMMY_GROUP_ID)

    private fun createGroupPolicy(synchronisationProtocol: String): GroupPolicy {
        val r3comCert = StringEscapeUtils.escapeJson(
            ClassLoader.getSystemResource("r3Com.pem")!!.readText()
                .replace("\r", "")
                .replace("\n", System.lineSeparator())
        )

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
                          "$r3comCert"
                        ],
                        "$TLS_PKI": "$STANDARD",
                        "$TLS_VERSION": "$VERSION_1_3",
                        "$TLS_TYPE": "${ONE_WAY.groupPolicyName}",
                        "$PROTOCOL_MODE": "$AUTH_ENCRYPT"
                    },
                    "$CIPHER_SUITE": {}
                }
            """.trimIndent()
            )
        )
    }

    private fun mockGroupPolicy(groupPolicy: GroupPolicy?, holdingIdentity: HoldingIdentity) =
        doReturn(groupPolicy).whenever(groupPolicyProvider).getGroupPolicy(holdingIdentity)

    @BeforeEach
    fun setUp() {
        synchronisationProxy = SynchronisationProxyImpl(
            lifecycleCoordinatorFactory,
            subscriptionFactory,
            configReadService,
            groupPolicyProvider,
            memberSyncProtocols + mgmSyncProtocols + syncProtocols
        )
        memberSyncProtocols.forEach { it.started = 0 }
        mgmSyncProtocols.forEach { it.started = 0 }
    }

    @AfterEach
    fun tearDown() {
        classes.clear()
    }

    private fun postStartEvent() {
        lifecycleHandlerCaptor.firstValue.processEvent(StartEvent(), coordinator)
    }

    private fun postStopEvent() {
        lifecycleHandlerCaptor.firstValue.processEvent(StopEvent(), coordinator)
    }

    private fun postRegistrationStatusChangeEvent(
        status: LifecycleStatus,
        handle: RegistrationHandle = subHandle
    ) {
        lifecycleHandlerCaptor.firstValue.processEvent(
            RegistrationStatusChangeEvent(
                handle,
                status
            ),
            coordinator
        )
    }

    private fun postConfigChangedEvent() {
        lifecycleHandlerCaptor.firstValue.processEvent(
            ConfigChangedEvent(
                setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG),
                mapOf(
                    ConfigKeys.BOOT_CONFIG to testConfig,
                    ConfigKeys.MESSAGING_CONFIG to testConfig
                )
            ), coordinator
        )
    }

    @Test
    fun `Proxy selects correct synchronisation protocol`() {
        postConfigChangedEvent()
        postRegistrationStatusChangeEvent(LifecycleStatus.UP)
        synchronisationProxy.start()
        val identity1 = createHoldingIdentity()
        mockGroupPolicy(createGroupPolicy(MemberSyncProtocol1::class.java.name), identity1)
        processor.firstValue.onNext(processMembershipUpdatesRecord)
        assertThat(classes.last()).isEqualTo(MemberSyncProtocol1::class.java)

        val identity2 = createHoldingIdentity()
        mockGroupPolicy(createGroupPolicy(MemberSyncProtocol2::class.java.name), identity2)
        processor.firstValue.onNext(processMembershipUpdatesRecord)
        assertThat(classes.last()).isEqualTo(MemberSyncProtocol2::class.java)

        val identity3 = createHoldingIdentity()
        mockGroupPolicy(createGroupPolicy(MgmSyncProtocol1::class.java.name), identity3)
        processor.firstValue.onNext(processSyncRequestRecord)
        assertThat(classes.last()).isEqualTo(MgmSyncProtocol1::class.java)
    }

    @Test
    fun `Proxy does nothing when exception occurs during processing sync command`() {
        postConfigChangedEvent()
        postRegistrationStatusChangeEvent(LifecycleStatus.UP)
        synchronisationProxy.start()
        val identity = createHoldingIdentity()
        // this will result in an exception but that exception will be caught by the processor
        mockGroupPolicy(createGroupPolicy(MgmSyncProtocol1::class.java.name), identity)
        processor.firstValue.onNext(processMembershipUpdatesRecord)
        assertThat(classes).isEmpty()
    }

    @Test
    fun `Proxy does nothing when non-handled sync command type is used`() {
        postConfigChangedEvent()
        postRegistrationStatusChangeEvent(LifecycleStatus.UP)
        synchronisationProxy.start()
        processor.firstValue.onNext(listOf(Record("sync_topic", "test_key", SynchronisationCommand("command"))))
        assertThat(classes).isEmpty()
    }

    @Test
    fun `Proxy does nothing when SynchronisationException is thrown`() {
        postConfigChangedEvent()
        postRegistrationStatusChangeEvent(LifecycleStatus.UP)
        synchronisationProxy.start()
        processor.firstValue.onNext(listOf(Record("sync_topic", "test_key", null)))
        assertThat(classes).isEmpty()
    }

    @Test
    fun `Proxy throws exception when group policy cannot be retrieved`() {
        postConfigChangedEvent()
        postRegistrationStatusChangeEvent(LifecycleStatus.UP)
        synchronisationProxy.start()
        whenever(groupPolicyProvider.getGroupPolicy(any())).thenThrow(BadGroupPolicyException("Failed to get group policy."))
        val ex = assertFailsWith<SynchronisationProtocolSelectionException> {
            synchronisationProxy.processMembershipUpdates(updates)
        }
        assertThat(ex.message).isEqualTo("Failed to select correct synchronisation protocol due to problems retrieving the group policy.")
    }

    @Test
    fun `Proxy throws exception when wrong sync interface is used for given command`() {
        postConfigChangedEvent()
        postRegistrationStatusChangeEvent(LifecycleStatus.UP)
        synchronisationProxy.start()
        val identity = createHoldingIdentity()
        mockGroupPolicy(createGroupPolicy(MgmSyncProtocol1::class.java.name), identity)
        val ex = assertFailsWith<SynchronisationProtocolTypeException> {
            synchronisationProxy.processMembershipUpdates(updates)
        }
        assertThat(ex.message).isEqualTo("Wrong synchronisation service type was configured in group policy file.")
    }

    @Test
    fun `Proxy throws exception when sync protocol is neither mgm or member interface`() {
        postConfigChangedEvent()
        postRegistrationStatusChangeEvent(LifecycleStatus.UP)
        synchronisationProxy.start()
        val identity = createHoldingIdentity()
        mockGroupPolicy(createGroupPolicy(AbstractSyncProtocol::class.java.name), identity)
        val ex = assertFailsWith<SynchronisationProtocolTypeException> {
            synchronisationProxy.processMembershipUpdates(updates)
        }
        assertThat(ex.message).isEqualTo("Wrong synchronisation service type was configured in group policy file.")
    }

    @Test
    fun `Proxy throws exception for invalid synchronisation protocol config`() {
        postConfigChangedEvent()
        postRegistrationStatusChangeEvent(LifecycleStatus.UP)
        synchronisationProxy.start()
        val identity = createHoldingIdentity()
        mockGroupPolicy(createGroupPolicy(String::class.java.name), identity)
        assertFailsWith<SynchronisationProtocolSelectionException> {
            synchronisationProxy.processMembershipUpdates(updates)
        }
    }

    @Test
    fun `Proxy throws exception when synchronisation protocol cannot be found for holding identity`() {
        postConfigChangedEvent()
        postRegistrationStatusChangeEvent(LifecycleStatus.UP)
        synchronisationProxy.start()
        val identity = createHoldingIdentity()
        mockGroupPolicy(null, identity)
        val ex = assertFailsWith<SynchronisationProtocolSelectionException> {
            synchronisationProxy.processMembershipUpdates(updates)
        }
        assertThat(ex.message).contains("Could not find group policy file for holding identity")
    }

    @Test
    fun `Start calls start on coordinator`() {
        synchronisationProxy.start()
        verify(coordinator).start()
        assertThat(synchronisationProxy.isRunning).isTrue
    }

    @Test
    fun `Stop calls stop on coordinator`() {
        synchronisationProxy.stop()
        verify(coordinator).stop()
        assertThat(synchronisationProxy.isRunning).isFalse
    }

    @Test
    fun `Service API fails for processing updates when service is not running`() {
        doReturn(false).whenever(coordinator).isRunning
        val identity = createHoldingIdentity()
        mockGroupPolicy(createGroupPolicy(MemberSyncProtocol1::class.java.name), identity)
        assertFailsWith<IllegalStateException> { synchronisationProxy.processMembershipUpdates(mock()) }
    }

    @Test
    fun `Service API fails for processing sync requests when service is not running`() {
        doReturn(false).whenever(coordinator).isRunning
        val identity = createHoldingIdentity()
        mockGroupPolicy(createGroupPolicy(MgmSyncProtocol1::class.java.name), identity)
        assertFailsWith<IllegalStateException> { synchronisationProxy.processSyncRequest(mock()) }
    }

    @Test
    fun `Service API fails when coordinator is ERROR`() {
        doReturn(true).whenever(coordinator).isRunning
        doReturn(LifecycleStatus.ERROR).whenever(coordinator).status
        val identity = createHoldingIdentity()
        mockGroupPolicy(createGroupPolicy(MemberSyncProtocol1::class.java.name), identity)
        assertFailsWith<IllegalStateException> { synchronisationProxy.processMembershipUpdates(mock()) }
    }

    @Test
    fun `start event starts synchronisation protocols and follows statuses of dependencies`() {
        postStartEvent()

        verify(coordinator).followStatusChangesByName(dependencies)
        verify(dependencyHandle, never()).close()
        assertThat(memberSyncProtocol1.started).isEqualTo(1)
        assertThat(memberSyncProtocol2.started).isEqualTo(1)
        assertThat(mgmSyncProtocol1.started).isEqualTo(1)
        assertThat(syncProtocol1.started).isEqualTo(1)

        verify(coordinator, never()).updateStatus(any(), any())
    }

    @Test
    fun `start event called a second time closes previously created registration handle`() {
        postStartEvent()
        postStartEvent()

        verify(coordinator, times(2)).followStatusChangesByName(dependencies)
        verify(dependencyHandle).close()
        assertThat(memberSyncProtocol1.started).isEqualTo(2)
        assertThat(memberSyncProtocol2.started).isEqualTo(2)
        assertThat(mgmSyncProtocol1.started).isEqualTo(2)
        assertThat(syncProtocol1.started).isEqualTo(2)
        verify(coordinator, never()).updateStatus(any(), any())
    }

    @Test
    fun `stop event before start event doesn't close registration handle and sets status to down`() {
        postStopEvent()

        verify(dependencyHandle, never()).close()
        assertThat(coordinator.status).isEqualTo(LifecycleStatus.DOWN)
    }

    @Test
    fun `stop event after start event closes registration handle and sets status to down`() {
        postStartEvent()
        postStopEvent()

        verify(dependencyHandle).close()
        assertThat(coordinator.status).isEqualTo(LifecycleStatus.DOWN)
    }

    @Test
    fun `Registration changed event DOWN sets coordinator status DOWN`() {
        postRegistrationStatusChangeEvent(LifecycleStatus.DOWN)

        assertThat(coordinator.status).isEqualTo(LifecycleStatus.DOWN)
    }

    @Test
    fun `Registration changed event UP sets coordinator status UP`() {
        postConfigChangedEvent()
        verify(subHandle, never()).close()
        verify(subscription, never()).close()
        postRegistrationStatusChangeEvent(LifecycleStatus.UP)

        assertThat(coordinator.status).isEqualTo(LifecycleStatus.UP)

        postConfigChangedEvent()
        verify(subHandle, times(1)).close()
        verify(subscription, times(1)).close()
    }

    @Test
    fun `Registration changed event registers for component updates and closes config handle when available`() {
        postStartEvent()
        postRegistrationStatusChangeEvent(LifecycleStatus.UP, dependencyHandle)
        verify(configHandle, never()).close()
        verify(configReadService, times(1)).registerComponentForUpdates(any(), any())

        postRegistrationStatusChangeEvent(LifecycleStatus.UP, dependencyHandle)
        verify(configHandle, times(1)).close()
        verify(configReadService, times(2)).registerComponentForUpdates(any(), any())
    }

    class MemberSyncProtocol1 : AbstractMemberSyncProtocol() {
        override fun processMembershipUpdates(updates: ProcessMembershipUpdates) {
            classes.add(MemberSyncProtocol1::class.java)
        }
    }

    class MemberSyncProtocol2 : AbstractMemberSyncProtocol() {
        override fun processMembershipUpdates(updates: ProcessMembershipUpdates) {
            classes.add(MemberSyncProtocol2::class.java)
        }
    }

    abstract class AbstractMemberSyncProtocol : MemberSynchronisationService {
        var started = 0

        override fun processMembershipUpdates(updates: ProcessMembershipUpdates) {
            classes.add(AbstractMemberSyncProtocol::class.java)
        }

        override val isRunning = true
        override fun start() { started += 1 }
        override fun stop() {}
    }

    class MgmSyncProtocol1 : AbstractMgmSyncProtocol() {
        override fun processSyncRequest(request: ProcessSyncRequest) {
            classes.add(MgmSyncProtocol1::class.java)
        }
    }

    abstract class AbstractMgmSyncProtocol : MgmSynchronisationService {
        var started = 0

        override fun processSyncRequest(request: ProcessSyncRequest) {
            classes.add(AbstractMgmSyncProtocol::class.java)
        }

        override val isRunning = true
        override fun start() { started += 1 }
        override fun stop() {}
    }

    class AbstractSyncProtocol : SynchronisationService {
        var started = 0

        override val isRunning = true
        override fun start() { started += 1 }
        override fun stop() {}
    }
}
