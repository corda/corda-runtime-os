package net.corda.membership.impl.grouppolicy

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.data.membership.event.MembershipEvent
import net.corda.data.membership.event.registration.MgmOnboarded
import net.corda.layeredpropertymap.testkit.LayeredPropertyMapMocks
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.Resource
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.impl.grouppolicy.GroupPolicyProviderImpl.FinishedRegistrationsProcessor
import net.corda.membership.lib.exceptions.BadGroupPolicyException
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.lib.grouppolicy.GroupPolicyParser
import net.corda.membership.lib.grouppolicy.MGMGroupPolicy
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.app.AppMessage
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoListener
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.Captor
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

/**
 * Unit tests for [GroupPolicyProviderImpl]
 */
class GroupPolicyProviderImplTest {
    lateinit var groupPolicyProvider: GroupPolicyProviderImpl

    val groupIdKey = "groupId"
    val registrationProtocolKey = "registrationProtocol"

    val groupId1 = "ABC123"
    val groupId2 = "DEF456"

    val regProtocol1 = "foo"
    val regProtocol2 = "bar"
    val regProtocol3 = "baz"

    val alice = MemberX500Name("Alice", "London", "GB")
    val bob = MemberX500Name("Bob", "London", "GB")
    val mgm = MemberX500Name("MGM", "London", "GB")

    val groupPolicy1 = "{\"$registrationProtocolKey\": \"$regProtocol1\", \"$groupIdKey\": \"$groupId1\"}"
    val groupPolicy2 = "{\"$registrationProtocolKey\": \"$regProtocol2\", \"$groupIdKey\": \"$groupId1\"}"
    val groupPolicy3 = "{\"$registrationProtocolKey\": \"$regProtocol3\", \"$groupIdKey\": \"$groupId2\"}"
    val groupPolicy4: String? = null
    val groupPolicy5 = "{\"$registrationProtocolKey\": \"$regProtocol3\", \"$groupIdKey\": \"$groupId2\"}"

    private val parsedGroupPolicy1: GroupPolicy = mock {
        on { groupId } doReturn groupId1
        on { registrationProtocol }.doReturn(regProtocol1)
    }
    private val parsedGroupPolicy2: GroupPolicy = mock {
        on { groupId } doReturn groupId1
        on { registrationProtocol }.doReturn(regProtocol2)
    }
    private val parsedGroupPolicy3: GroupPolicy = mock {
        on { groupId } doReturn groupId2
        on { registrationProtocol }.doReturn(regProtocol3)
    }
    private val parsedMgmGroupPolicy: MGMGroupPolicy = mock()

    val holdingIdentity1 = HoldingIdentity(alice, groupId1)
    val holdingIdentity2 = HoldingIdentity(bob, groupId1)
    val holdingIdentity3 = HoldingIdentity(alice, groupId2)
    val holdingIdentity4 = HoldingIdentity(bob, groupId2)
    val holdingIdentity5 = HoldingIdentity(mgm, groupId2)

    fun mockMetadata(resultGroupPolicy: String?) = mock<CpiMetadata> {
        on { groupPolicy } doReturn resultGroupPolicy
    }

    val cpiMetadata1 = mockMetadata(groupPolicy1)
    val cpiMetadata2 = mockMetadata(groupPolicy2)
    val cpiMetadata3 = mockMetadata(groupPolicy3)
    val cpiMetadata4 = mockMetadata(groupPolicy4)
    val cpiMetadata5 = mockMetadata(groupPolicy5)

    val cpiIdentifier1: CpiIdentifier = mock()
    val cpiIdentifier2: CpiIdentifier = mock()
    val cpiIdentifier3: CpiIdentifier = mock()
    val cpiIdentifier4: CpiIdentifier = mock()
    val cpiIdentifier5: CpiIdentifier = mock()

    var virtualNodeListener: VirtualNodeInfoListener? = null

    fun createVirtualNodeInfo(holdingIdentity: HoldingIdentity, cpiIdentifier: CpiIdentifier) = VirtualNodeInfo(
        holdingIdentity, cpiIdentifier, null, UUID.randomUUID(), null, UUID.randomUUID(), null, UUID.randomUUID(), timestamp = Instant.now()
    )

    val virtualNodeInfoReadService: VirtualNodeInfoReadService = mock {
        on { get(eq(holdingIdentity1)) } doReturn createVirtualNodeInfo(holdingIdentity1, cpiIdentifier1)
        on { get(eq(holdingIdentity2)) } doReturn createVirtualNodeInfo(holdingIdentity2, cpiIdentifier2)
        on { get(eq(holdingIdentity3)) } doReturn createVirtualNodeInfo(holdingIdentity3, cpiIdentifier3)
        on { get(eq(holdingIdentity4)) } doReturn createVirtualNodeInfo(holdingIdentity4, cpiIdentifier4)
        on { get(eq(holdingIdentity5)) } doReturn createVirtualNodeInfo(holdingIdentity5, cpiIdentifier5)
        on { registerCallback(any()) } doAnswer {
            virtualNodeListener = it.arguments[0] as VirtualNodeInfoListener
            mock()
        }
    }

    val cpiInfoReader: CpiInfoReadService = mock {
        on { get(cpiIdentifier1) } doReturn cpiMetadata1
        on { get(cpiIdentifier2) } doReturn cpiMetadata2
        on { get(cpiIdentifier3) } doReturn cpiMetadata3
        on { get(cpiIdentifier4) } doReturn cpiMetadata4
        on { get(cpiIdentifier5) } doReturn cpiMetadata5
    }

    @Captor
    private val lifecycleEventHandler = argumentCaptor<LifecycleEventHandler>()
    private val configs = setOf(BOOT_CONFIG, MESSAGING_CONFIG)
    private val dependencies = setOf(
        LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>(),
        LifecycleCoordinatorName.forComponent<CpiInfoReadService>(),
        LifecycleCoordinatorName.forComponent<MembershipQueryClient>(),
        LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
    )
    private val dependencyServiceRegistration: RegistrationHandle = mock()
    private val subRegistration: RegistrationHandle = mock()
    private val configHandle: Resource = mock()
    private val subscription: Subscription<String, MembershipEvent> = mock()
    private val coordinator: LifecycleCoordinator = mock {
        on { followStatusChangesByName(any()) } doReturn subRegistration
        on {
            followStatusChangesByName(
                eq(dependencies)
            )
        } doReturn dependencyServiceRegistration
    }

    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), lifecycleEventHandler.capture()) } doReturn coordinator
    }
    private val configurationReadService: ConfigurationReadService = mock {
        on { registerComponentForUpdates(eq(coordinator), eq(configs)) } doReturn configHandle
    }
    private val subscriptionFactory: SubscriptionFactory = mock {
        on { createDurableSubscription(any(), any<FinishedRegistrationsProcessor>(), any(), eq(null)) } doReturn subscription
    }
    private val layeredPropertyMapFactory = LayeredPropertyMapMocks.createFactory()
    private val properties = layeredPropertyMapFactory.createMap(emptyMap())
    private val groupPolicyParser: GroupPolicyParser = mock {
        on { parse(eq(holdingIdentity1), eq(groupPolicy1), any()) }.doReturn(parsedGroupPolicy1)
        on { parse(eq(holdingIdentity1), eq(groupPolicy2), any()) }.doReturn(parsedGroupPolicy2)
        on { parse(eq(holdingIdentity2), eq(groupPolicy2), any()) }.doReturn(parsedGroupPolicy2)
        on { parse(eq(holdingIdentity3), eq(groupPolicy3), any()) }.doReturn(parsedGroupPolicy3)
        on { parse(eq(holdingIdentity4), eq(null), any()) }.doThrow(BadGroupPolicyException(""))
        on { parse(eq(holdingIdentity5), eq(groupPolicy3), any()) }.doReturn(parsedMgmGroupPolicy)
    }

    private val membershipQueryClient: MembershipQueryClient = mock {
        on { queryGroupPolicy(any()) }.doReturn(MembershipQueryResult.Success(properties))
    }

    private fun postStartEvent() = postEvent(StartEvent())
    private fun postStopEvent() = postEvent(StopEvent())

    private fun postRegistrationStatusChangeEvent(
        handle: RegistrationHandle,
        lifecycleStatus: LifecycleStatus = LifecycleStatus.UP
    ) = postEvent(RegistrationStatusChangeEvent(handle, lifecycleStatus))

    private fun postConfigChangedEvent() = postEvent(
        ConfigChangedEvent(
            setOf(MESSAGING_CONFIG),
            mapOf(MESSAGING_CONFIG to SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.empty()))
        )
    )

    private fun postEvent(event: LifecycleEvent) = lifecycleEventHandler.firstValue.processEvent(event, coordinator)

    // set up mock for new CPI and send update to virtual node callback
    private fun setCpi(holdingIdentity: HoldingIdentity, cpiIdentifier: CpiIdentifier) {
        val vnode = createVirtualNodeInfo(holdingIdentity, cpiIdentifier)
        doReturn(vnode).whenever(virtualNodeInfoReadService).get(holdingIdentity)
        virtualNodeListener?.onUpdate(
            setOf(holdingIdentity),
            mapOf(holdingIdentity to vnode)
        )
    }

    @BeforeEach
    fun setUp() {
        groupPolicyProvider = GroupPolicyProviderImpl(
            virtualNodeInfoReadService,
            cpiInfoReader,
            lifecycleCoordinatorFactory,
            groupPolicyParser,
            membershipQueryClient,
            subscriptionFactory,
            configurationReadService
        )
    }

    fun startComponentAndDependencies() {
        postRegistrationStatusChangeEvent(subRegistration)
    }

    fun assertExpectedGroupPolicy(
        groupPolicy: GroupPolicy?,
        groupId: String?,
        registrationProtocol: String?,
    ) {
        assertNotNull(groupPolicy)
        assertEquals(groupId, groupPolicy?.groupId)
        assertEquals(registrationProtocol, groupPolicy?.registrationProtocol)
    }

    @Test
    fun `Correct group policy is returned when CPI metadata contains group policy string and service has started`() {
        postConfigChangedEvent()
        startComponentAndDependencies()
        assertExpectedGroupPolicy(
            groupPolicyProvider.getGroupPolicy(holdingIdentity1),
            groupId1,
            regProtocol1
        )
        assertExpectedGroupPolicy(
            groupPolicyProvider.getGroupPolicy(holdingIdentity2),
            groupId1,
            regProtocol2
        )
        assertExpectedGroupPolicy(
            groupPolicyProvider.getGroupPolicy(holdingIdentity3),
            groupId2,
            regProtocol3
        )
        assertThat(groupPolicyProvider.getGroupPolicy(holdingIdentity4)).isNull()
    }

    @Test
    fun `Group policy read fails if service hasn't started`() {
        assertThrows<IllegalStateException> { groupPolicyProvider.getGroupPolicy(holdingIdentity1) }
    }

    @Test
    fun `Group policy read fails if service isn't up`() {
        groupPolicyProvider.start()
        assertThrows<IllegalStateException> { groupPolicyProvider.getGroupPolicy(holdingIdentity1) }
    }

    @Test
    fun `Same group policy is returned if it has already been parsed`() {
        postConfigChangedEvent()
        startComponentAndDependencies()
        val result1 = groupPolicyProvider.getGroupPolicy(holdingIdentity1)
        val result2 = groupPolicyProvider.getGroupPolicy(holdingIdentity1)

        assertEquals(result1, result2)
        verify(groupPolicyParser, times(1)).parse(any(), any(), any())
    }

    @Test
    fun `Cache is cleared and group policy is parsed again if the service restarts`() {
        postConfigChangedEvent()
        startComponentAndDependencies()
        groupPolicyProvider.getGroupPolicy(holdingIdentity1)
        groupPolicyProvider.stop()
        startComponentAndDependencies()
        groupPolicyProvider.getGroupPolicy(holdingIdentity1)

        verify(groupPolicyParser, times(2)).parse(any(), any(), any())
    }

    @Test
    fun `MGM group policy is cached`() {
        postConfigChangedEvent()
        startComponentAndDependencies()
        groupPolicyProvider.getGroupPolicy(holdingIdentity5)
        groupPolicyProvider.getGroupPolicy(holdingIdentity5)
        groupPolicyProvider.getGroupPolicy(holdingIdentity5)

        verify(groupPolicyParser, times(1)).parse(any(), any(), any())
    }

    @Test
    fun `start event starts the coordinator`() {
        groupPolicyProvider.start()
        verify(coordinator).start()
    }

    @Test
    fun `stop event stops the coordinator`() {
        groupPolicyProvider.stop()
        verify(coordinator).stop()
    }

    @Test
    fun `start event creates new dependency registration handle`() {
        postStartEvent()

        verify(dependencyServiceRegistration, never()).close()
        verify(coordinator).followStatusChangesByName(eq(dependencies))
    }

    @Test
    fun `start event closes old registration handle and creates new registration handle if one exists`() {
        postStartEvent()
        postStartEvent()

        verify(dependencyServiceRegistration).close()
        verify(coordinator, times(2)).followStatusChangesByName(eq(dependencies))
    }

    @Test
    fun `stop event sets status to down but closes no handles or subscriptions if they don't exist yet`() {
        postStopEvent()

        verify(dependencyServiceRegistration, never()).close()
        verify(subRegistration, never()).close()
        verify(configHandle, never()).close()
        verify(subscription, never()).close()
        verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
    }

    @Test
    fun `stop event sets status to down and closes handles and subscription when they have been created`() {
        postStartEvent()
        postRegistrationStatusChangeEvent(dependencyServiceRegistration)
        postConfigChangedEvent()
        postStopEvent()

        verify(dependencyServiceRegistration).close()
        verify(configHandle).close()
        verify(subscription).close()
        verify(coordinator).updateStatus(
            eq(LifecycleStatus.DOWN), any()
        )
    }

    @Test
    fun `registration status change to UP follows config changes`() {
        postStartEvent()
        postRegistrationStatusChangeEvent(dependencyServiceRegistration)

        verify(configHandle, never()).close()
        verify(configurationReadService).registerComponentForUpdates(
            eq(coordinator),
            eq(setOf(BOOT_CONFIG, MESSAGING_CONFIG))
        )
    }

    @Test
    fun `registration status change to UP a second time recreates the config change handle`() {
        postStartEvent()
        postRegistrationStatusChangeEvent(dependencyServiceRegistration)
        postRegistrationStatusChangeEvent(dependencyServiceRegistration)

        verify(configHandle).close()
        verify(configurationReadService, times(2)).registerComponentForUpdates(
            eq(coordinator),
            eq(setOf(BOOT_CONFIG, MESSAGING_CONFIG))
        )
    }

    @Test
    fun `registration status change to DOWN set the component status to down`() {
        postStartEvent()
        postRegistrationStatusChangeEvent(dependencyServiceRegistration, LifecycleStatus.DOWN)

        verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
        verify(subscription, never()).close()
    }

    @Test
    fun `registration status change to DOWN set the component status to down and closes the subscription if already created`() {
        postStartEvent()
        postConfigChangedEvent()
        postRegistrationStatusChangeEvent(dependencyServiceRegistration, LifecycleStatus.DOWN)

        verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
        verify(subscription).close()
    }

    @Test
    fun `config changed event creates subscription`() {
        postConfigChangedEvent()

        verify(subscription, never()).close()
        verify(subscriptionFactory).createDurableSubscription(
            any(),
            any<DurableProcessor<String, AppMessage>>(),
            any(),
            eq(null)
        )
        verify(subscription).start()
        verify(coordinator).followStatusChangesByName(any())
    }

    @Test
    fun `component starts after subscription is UP`() {
        postConfigChangedEvent()
        postRegistrationStatusChangeEvent(subRegistration)

        verify(coordinator).updateStatus(eq(LifecycleStatus.UP), any())
    }

    @Test
    fun `config changed event closes original subscription before creating a new one`() {
        postConfigChangedEvent()
        postConfigChangedEvent()

        verify(subscription).close()
        verify(subscriptionFactory, times(2)).createDurableSubscription(
            any(),
            any<DurableProcessor<String, AppMessage>>(),
            any(),
            eq(null)
        )
        verify(subscription, times(2)).start()
        verify(coordinator, times(2)).followStatusChangesByName(any())
    }

    @Test
    fun `Cached group policy is updated when a holding identity updates their CPI`() {
        postConfigChangedEvent()
        assertNull(virtualNodeListener)
        startComponentAndDependencies()
        assertNotNull(virtualNodeListener)
        val original = groupPolicyProvider.getGroupPolicy(holdingIdentity1)
        assertExpectedGroupPolicy(original, groupId1, regProtocol1)

        virtualNodeListener?.onUpdate(
            setOf(holdingIdentity1),
            mapOf(
                holdingIdentity1 to VirtualNodeInfo(
                    holdingIdentity1,
                    cpiIdentifier2,
                    null,
                    UUID.randomUUID(),
                    null,
                    UUID.randomUUID(),
                    null,
                    UUID.randomUUID(),
                    timestamp = Instant.now()
                )
            )
        )

        val updated = groupPolicyProvider.getGroupPolicy(holdingIdentity1)
        assertNotEquals(original, updated)
        assertExpectedGroupPolicy(updated, groupId1, regProtocol2)
    }

    @Test
    fun `Group policy not yet cached is created when a holding identity updates their CPI`() {
        assertNull(virtualNodeListener)
        postConfigChangedEvent()
        startComponentAndDependencies()
        assertNotNull(virtualNodeListener)

        virtualNodeListener?.onUpdate(
            setOf(holdingIdentity1),
            mapOf(
                holdingIdentity1 to VirtualNodeInfo(
                    holdingIdentity1,
                    cpiIdentifier2,
                    null,
                    UUID.randomUUID(),
                    null,
                    UUID.randomUUID(),
                    null,
                    UUID.randomUUID(),
                    timestamp = Instant.now()
                )
            )
        )

        val updated = groupPolicyProvider.getGroupPolicy(holdingIdentity1)
        assertExpectedGroupPolicy(updated, groupId1, regProtocol2)
    }

    @Test
    fun `Component goes down when stop event is received and data can't be accessed`() {
        postStopEvent()
        verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())

        assertThrows<IllegalStateException> { groupPolicyProvider.getGroupPolicy(holdingIdentity1) }
    }

    @Test
    fun `Component goes down and up when followed components go down and up again`() {
        postConfigChangedEvent()
        startComponentAndDependencies()
        verify(coordinator).updateStatus(eq(LifecycleStatus.UP), any())
        assertNotNull(lifecycleEventHandler)

        postRegistrationStatusChangeEvent(dependencyServiceRegistration, LifecycleStatus.DOWN)
        postRegistrationStatusChangeEvent(dependencyServiceRegistration)
        postRegistrationStatusChangeEvent(subRegistration)

        assertExpectedGroupPolicy(
            groupPolicyProvider.getGroupPolicy(holdingIdentity1),
            groupId1,
            regProtocol1
        )
    }

    @Test
    fun `Group policy is removed from cache if exception occurs when parsing during virtual node update callback`() {
        postConfigChangedEvent()
        // start component
        startComponentAndDependencies()

        // test holding identity
        val holdingIdentity = HoldingIdentity(alice, "FOO-BAR")

        doReturn(parsedGroupPolicy1).whenever(groupPolicyParser).parse(eq(holdingIdentity), eq(groupPolicy1), any())
        doThrow(BadGroupPolicyException("")).whenever(groupPolicyParser).parse(eq(holdingIdentity), eq(null), any())

        // Configure initial CPI with valid group policy for holding identity
        setCpi(holdingIdentity, cpiIdentifier1)

        // Look up group policy to set initial cache value
        val initial = groupPolicyProvider.getGroupPolicy(holdingIdentity)
        assertExpectedGroupPolicy(initial, groupId1, regProtocol1)

        // Trigger callback where an invalid group policy is loaded
        // This should cause an exception in parsing which is caught and the cached value should be removed
        assertDoesNotThrow { setCpi(holdingIdentity, cpiIdentifier4) }

        // Now there is no cached value so the service will parse again instead of reading from the cache.
        // Assert for exception to prove we are now parsing and not relying on the cache.
        assertThat(groupPolicyProvider.getGroupPolicy(holdingIdentity)).isNull()

        // reset to valid group policy
        assertDoesNotThrow { setCpi(holdingIdentity, cpiIdentifier1) }

        // group policy retrieval works again
        val result = groupPolicyProvider.getGroupPolicy(holdingIdentity)
        assertExpectedGroupPolicy(result, groupId1, regProtocol1)
    }

    @Test
    fun `MGM group policy is not cached and returned on virtual node update callback`() {
        postConfigChangedEvent()
        startComponentAndDependencies()

        setCpi(holdingIdentity5, cpiIdentifier5)
        verify(groupPolicyParser, times(1)).parse(eq(holdingIdentity5), any(), any())

        // checking that group policy is parsed again because it wasn't cached on virtual node update
        groupPolicyProvider.getGroupPolicy(holdingIdentity5)
        verify(groupPolicyParser, times(2)).parse(eq(holdingIdentity5), any(), any())
    }

    @Test
    fun `MGM group policy is cached on mgm onboarded event`() {
        postConfigChangedEvent()
        startComponentAndDependencies()

        groupPolicyProvider.FinishedRegistrationsProcessor()
            .onNext(listOf(Record("", "", MembershipEvent(MgmOnboarded(holdingIdentity5.toAvro())))))
        verify(groupPolicyParser, times(1)).parse(eq(holdingIdentity5), any(), any())

        groupPolicyProvider.getGroupPolicy(holdingIdentity5)
        verify(groupPolicyParser, times(1)).parse(eq(holdingIdentity5), any(), any())
    }

    @Test
    fun `MGM group policy is not cached on mgm onboarded event when group policy cannot be parsed`() {
        postConfigChangedEvent()
        startComponentAndDependencies()

        whenever(groupPolicyParser.parse(eq(holdingIdentity5), any(), any())).thenReturn(null)

        groupPolicyProvider.FinishedRegistrationsProcessor()
            .onNext(listOf(Record("", "", MembershipEvent(MgmOnboarded(holdingIdentity5.toAvro())))))
        verify(groupPolicyParser, times(1)).parse(eq(holdingIdentity5), any(), any())

        groupPolicyProvider.getGroupPolicy(holdingIdentity5)
        verify(groupPolicyParser, times(2)).parse(eq(holdingIdentity5), any(), any())
    }


    @Test
    fun `MGM group policy is removed from cache if exception occurs when parsing`() {
        postConfigChangedEvent()
        startComponentAndDependencies()

        groupPolicyProvider.FinishedRegistrationsProcessor()
            .onNext(listOf(Record("", "", MembershipEvent(MgmOnboarded(holdingIdentity5.toAvro())))))
        verify(groupPolicyParser, times(1)).parse(eq(holdingIdentity5), any(), any())

        // returns from cache
        groupPolicyProvider.getGroupPolicy(holdingIdentity5)
        verify(groupPolicyParser, times(1)).parse(eq(holdingIdentity5), any(), any())

        // on new event we will fail parsing
        whenever(groupPolicyParser.parse(eq(holdingIdentity5), any(), any())).thenReturn(null)
        groupPolicyProvider.FinishedRegistrationsProcessor()
            .onNext(listOf(Record("", "", MembershipEvent(MgmOnboarded(holdingIdentity5.toAvro())))))
        verify(groupPolicyParser, times(2)).parse(eq(holdingIdentity5), any(), any())

        // previous value was rmeoved from cache, hence re-calculating
        groupPolicyProvider.getGroupPolicy(holdingIdentity5)
        verify(groupPolicyParser, times(3)).parse(eq(holdingIdentity5), any(), any())
    }

    @Test
    fun `MGM group policy cache is updated on each mgm onboarded event`() {
        postConfigChangedEvent()
        startComponentAndDependencies()

        whenever(groupPolicyParser.parse(eq(holdingIdentity5), any(), any())).thenReturn(null)

        groupPolicyProvider.FinishedRegistrationsProcessor()
            .onNext(listOf(Record("", "", MembershipEvent(MgmOnboarded(holdingIdentity5.toAvro())))))
        verify(groupPolicyParser, times(1)).parse(eq(holdingIdentity5), any(), any())

        groupPolicyProvider.FinishedRegistrationsProcessor()
            .onNext(listOf(Record("", "", MembershipEvent(MgmOnboarded(holdingIdentity5.toAvro())))))
        verify(groupPolicyParser, times(2)).parse(eq(holdingIdentity5), any(), any())
    }

    @Test
    fun `Persisted group policy properties are null if error occurs when querying`() {
        postConfigChangedEvent()
        startComponentAndDependencies()
        val argCap = argumentCaptor<() -> LayeredPropertyMap?>()

        whenever(membershipQueryClient.queryGroupPolicy(any()))
            .doThrow(
                MembershipQueryResult.QueryException(
                    MembershipQueryResult.Failure<LayeredPropertyMap>("")
                )
            )
        groupPolicyProvider.getGroupPolicy(holdingIdentity1)
        verify(groupPolicyParser).parse(any(), any(), argCap.capture())
        assertThat(argCap.firstValue.invoke()).isNull()
    }

    @Test
    fun `Persisted group policy properties are return if no error occurs when querying`() {
        postConfigChangedEvent()
        startComponentAndDependencies()
        val argCap = argumentCaptor<() -> LayeredPropertyMap?>()

        whenever(membershipQueryClient.queryGroupPolicy(any()))
            .doReturn(MembershipQueryResult.Success(properties))
        groupPolicyProvider.getGroupPolicy(holdingIdentity1)
        verify(groupPolicyParser).parse(any(), any(), argCap.capture())
        assertThat(argCap.firstValue.invoke()).isEqualTo(properties)
    }
}
