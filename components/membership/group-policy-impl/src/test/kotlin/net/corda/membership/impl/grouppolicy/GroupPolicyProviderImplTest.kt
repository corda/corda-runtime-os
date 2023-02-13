package net.corda.membership.impl.grouppolicy

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentMemberInfo
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
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.exceptions.BadGroupPolicyException
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.lib.grouppolicy.GroupPolicyParser
import net.corda.membership.lib.grouppolicy.InteropGroupPolicyParser
import net.corda.membership.lib.grouppolicy.MGMGroupPolicy
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
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
import java.util.*

/**
 * Unit tests for [GroupPolicyProviderImpl]
 */
class GroupPolicyProviderImplTest {
    private lateinit var groupPolicyProvider: GroupPolicyProviderImpl

    private val groupIdKey = "groupId"
    private val registrationProtocolKey = "registrationProtocol"

    private val groupId1 = "ABC123"
    private val groupId2 = "DEF456"

    private val regProtocol1 = "foo"
    private val regProtocol2 = "bar"
    private val regProtocol3 = "baz"

    private val alice = MemberX500Name("Alice", "London", "GB")
    private val bob = MemberX500Name("Bob", "London", "GB")
    private val mgm = MemberX500Name("MGM", "London", "GB")

    private val groupPolicy1 = "{\"$registrationProtocolKey\": \"$regProtocol1\", \"$groupIdKey\": \"$groupId1\"}"
    private val groupPolicy2 = "{\"$registrationProtocolKey\": \"$regProtocol2\", \"$groupIdKey\": \"$groupId1\"}"
    private val groupPolicy3 = "{\"$registrationProtocolKey\": \"$regProtocol3\", \"$groupIdKey\": \"$groupId2\"}"
    private val groupPolicy4: String? = null
    private val groupPolicy5 = "{\"$registrationProtocolKey\": \"$regProtocol3\", \"$groupIdKey\": \"$groupId2\"}"

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

    private val holdingIdentity1 = HoldingIdentity(alice, groupId1)
    private val holdingIdentity2 = HoldingIdentity(bob, groupId1)
    private val holdingIdentity3 = HoldingIdentity(alice, groupId2)
    private val holdingIdentity4 = HoldingIdentity(bob, groupId2)
    private val holdingIdentity5 = HoldingIdentity(mgm, groupId2)

    private fun mockMetadata(resultGroupPolicy: String?) = mock<CpiMetadata> {
        on { groupPolicy } doReturn resultGroupPolicy
    }

    private val cpiMetadata1 = mockMetadata(groupPolicy1)
    private val cpiMetadata2 = mockMetadata(groupPolicy2)
    private val cpiMetadata3 = mockMetadata(groupPolicy3)
    private val cpiMetadata4 = mockMetadata(groupPolicy4)
    private val cpiMetadata5 = mockMetadata(groupPolicy5)

    private val cpiIdentifier1: CpiIdentifier = mock()
    private val cpiIdentifier2: CpiIdentifier = mock()
    private val cpiIdentifier3: CpiIdentifier = mock()
    private val cpiIdentifier4: CpiIdentifier = mock()
    private val cpiIdentifier5: CpiIdentifier = mock()

    private val validPersistentMemberInfo = PersistentMemberInfo(
        holdingIdentity5.toAvro(),
        KeyValuePairList(
            listOf(
                KeyValuePair(
                    MemberInfoExtension.PARTY_NAME,
                    holdingIdentity5.x500Name.toString(),
                ),
            ),
        ),
        KeyValuePairList(
            listOf(
                KeyValuePair(
                    MemberInfoExtension.IS_MGM,
                    "true",
                ),
                KeyValuePair(
                    MemberInfoExtension.STATUS,
                    MemberInfoExtension.MEMBER_STATUS_ACTIVE,
                ),
            ),
        ),
    )

    private var virtualNodeListener: VirtualNodeInfoListener? = null

    private fun createVirtualNodeInfo(holdingIdentity: HoldingIdentity, cpiIdentifier: CpiIdentifier) = VirtualNodeInfo(
        holdingIdentity, cpiIdentifier, null, UUID.randomUUID(), null, UUID.randomUUID(), null, UUID.randomUUID(), timestamp = Instant.now()
    )

    private val virtualNodeInfoReadService: VirtualNodeInfoReadService = mock {
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

    private val cpiInfoReader: CpiInfoReadService = mock {
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
    private val configHandle: Resource = mock()
    private val subscription: CompactedSubscription<String, PersistentMemberInfo> = mock()
    private val coordinator: LifecycleCoordinator = mock {
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
        on { createCompactedSubscription(any(), any<FinishedRegistrationsProcessor>(), any()) } doReturn subscription
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

    private val interopGroupPolicyParser: InteropGroupPolicyParser = mock {
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
            mapOf(MESSAGING_CONFIG to SmartConfigFactory.createWithoutSecurityServices().create(ConfigFactory.empty()))
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
            interopGroupPolicyParser,
            membershipQueryClient,
            subscriptionFactory,
            configurationReadService
        )
    }

    fun startComponentAndDependencies() {
        postConfigChangedEvent()
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
        startComponentAndDependencies()
        postConfigChangedEvent()
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
        verify(configHandle, never()).close()
        verify(subscription, never()).close()
        verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
    }

    @Test
    fun `stop event sets status to down and closes handles when they have been created`() {
        postStartEvent()
        postRegistrationStatusChangeEvent(dependencyServiceRegistration)
        postConfigChangedEvent()
        postStopEvent()

        verify(dependencyServiceRegistration).close()
        verify(configHandle).close()
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
        postConfigChangedEvent()
        postRegistrationStatusChangeEvent(dependencyServiceRegistration, LifecycleStatus.DOWN)

        verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
    }

    @Test
    fun `component starts after subscription is UP`() {
        postConfigChangedEvent()

        verify(coordinator).updateStatus(eq(LifecycleStatus.UP), any())
    }

    @Test
    fun `Cached group policy is updated when a holding identity updates their CPI`() {
        postConfigChangedEvent()
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
        startComponentAndDependencies()
        verify(coordinator).updateStatus(eq(LifecycleStatus.UP), any())
        assertNotNull(lifecycleEventHandler)

        postRegistrationStatusChangeEvent(dependencyServiceRegistration, LifecycleStatus.DOWN)
        postConfigChangedEvent()
        postRegistrationStatusChangeEvent(dependencyServiceRegistration)

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

        groupPolicyProvider.FinishedRegistrationsProcessor() {_, _ -> }
            .onNext(
                Record("", "", validPersistentMemberInfo),
                null,
                emptyMap()
            )
        verify(groupPolicyParser, times(1)).parse(eq(holdingIdentity5), any(), any())

        groupPolicyProvider.getGroupPolicy(holdingIdentity5)
        verify(groupPolicyParser, times(1)).parse(eq(holdingIdentity5), any(), any())
    }

    @Test
    fun `MGM group policy is not cached on mgm onboarded event when group policy cannot be parsed`() {
        postConfigChangedEvent()
        startComponentAndDependencies()

        whenever(groupPolicyParser.parse(eq(holdingIdentity5), any(), any())).thenReturn(null)

        groupPolicyProvider.FinishedRegistrationsProcessor()  {_, _ -> }
            .onNext(
                Record("", "", validPersistentMemberInfo),
                null,
                emptyMap(),
            )
        verify(groupPolicyParser, times(1)).parse(eq(holdingIdentity5), any(), any())

        groupPolicyProvider.getGroupPolicy(holdingIdentity5)
        verify(groupPolicyParser, times(2)).parse(eq(holdingIdentity5), any(), any())
    }


    @Test
    fun `MGM group policy is removed from cache if exception occurs when parsing`() {
        postConfigChangedEvent()
        startComponentAndDependencies()

        groupPolicyProvider.FinishedRegistrationsProcessor()  {_, _ -> }
            .onNext(
                Record("", "", validPersistentMemberInfo),
                null,
                emptyMap(),
            )
        verify(groupPolicyParser, times(1)).parse(eq(holdingIdentity5), any(), any())

        // returns from cache
        groupPolicyProvider.getGroupPolicy(holdingIdentity5)
        verify(groupPolicyParser, times(1)).parse(eq(holdingIdentity5), any(), any())

        // on new event we will fail parsing
        whenever(groupPolicyParser.parse(eq(holdingIdentity5), any(), any())).thenReturn(null)
        groupPolicyProvider.FinishedRegistrationsProcessor()  {_, _ -> }
            .onNext(
                Record("", "", validPersistentMemberInfo),
                null,
                emptyMap(),
            )
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

        groupPolicyProvider.FinishedRegistrationsProcessor()  {_, _ -> }
            .onNext(
                Record("", "", validPersistentMemberInfo),
                null,
                emptyMap(),
            )
        verify(groupPolicyParser, times(1)).parse(eq(holdingIdentity5), any(), any())

        groupPolicyProvider.FinishedRegistrationsProcessor()  {_, _ -> }
            .onNext(
                Record("", "", validPersistentMemberInfo),
                null,
                emptyMap(),
            )
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
        startComponentAndDependencies()
        postConfigChangedEvent()
        val argCap = argumentCaptor<() -> LayeredPropertyMap?>()

        whenever(membershipQueryClient.queryGroupPolicy(any()))
            .doReturn(MembershipQueryResult.Success(properties))
        groupPolicyProvider.getGroupPolicy(holdingIdentity1)
        verify(groupPolicyParser).parse(any(), any(), argCap.capture())
        assertThat(argCap.firstValue.invoke()).isEqualTo(properties)
    }

    @Test
    fun `registerListener will not start a subscription if the process is not running`() {
        groupPolicyProvider.registerListener("test") { _, _ ->
        }

        verify(subscription, never()).start()
    }

    @Test
    fun `registerListener will start the subscription if is running`() {
        startComponentAndDependencies()
        postConfigChangedEvent()
        groupPolicyProvider.registerListener("test") { _, _ ->
        }

        verify(subscription).start()
    }

    @Test
    fun `registerListener will start the subscription when running`() {
        groupPolicyProvider.registerListener("test") { _, _ ->
        }

        postConfigChangedEvent()

        verify(subscription).start()
    }

    @Test
    fun `registerListener will stop previous subscription with the same name`() {
        postConfigChangedEvent()
        groupPolicyProvider.registerListener("test1") { _, _ ->
        }
        groupPolicyProvider.registerListener("test1") { _, _ ->
        }
        groupPolicyProvider.registerListener("test2") { _, _ ->
        }

        verify(subscription, times(3)).start()
        verify(subscription, times(1)).close()
    }

    @Test
    fun `StopEvent will close all the subscriptions`() {
        postConfigChangedEvent()
        groupPolicyProvider.registerListener("test1") { _, _ ->
        }
        groupPolicyProvider.registerListener("test2") { _, _ ->
        }
        groupPolicyProvider.registerListener("test3") { _, _ ->
        }

        postStopEvent()

        verify(subscription, times(3)).close()
    }

    @Test
    fun `dependent DOWN will close all the subscriptions`() {
        postConfigChangedEvent()
        groupPolicyProvider.registerListener("test1") { _, _ ->
        }
        groupPolicyProvider.registerListener("test2") { _, _ ->
        }
        groupPolicyProvider.registerListener("test3") { _, _ ->
        }

        postRegistrationStatusChangeEvent(dependencyServiceRegistration, LifecycleStatus.DOWN)

        verify(subscription, times(3)).close()
    }

    @Test
    fun `second config change will take the subscription down and up`() {
        groupPolicyProvider.registerListener("test") { _, _ ->
        }

        postConfigChangedEvent()
        postConfigChangedEvent()

        verify(subscription, times(1)).close()
        verify(subscription, times(2)).start()
    }

    @Test
    fun `registerListener will call the call back when new virtual node is created`() {
        var holdingIdentity: HoldingIdentity? = null
        var groupPolicy: GroupPolicy? = null
        groupPolicyProvider.registerListener("test") { id, gp ->
            holdingIdentity = id
            groupPolicy = gp
        }
        postConfigChangedEvent()
        startComponentAndDependencies()

        virtualNodeListener?.onUpdate(
            setOf(holdingIdentity1),
            mapOf(
                holdingIdentity1 to VirtualNodeInfo(
                    holdingIdentity1,
                    cpiIdentifier2,
                    null,
                    UUID(0, 0),
                    null,
                    UUID(0, 0),
                    null,
                    UUID(0, 0),
                    timestamp = Instant.ofEpochSecond(100)
                )
            )
        )

        assertThat(holdingIdentity).isEqualTo(holdingIdentity1)
        assertThat(groupPolicy).isEqualTo(parsedGroupPolicy2)
    }

    @Test
    fun `registerListener will not call the call back when new MGM virtual node is created`() {
        var called = 0
        groupPolicyProvider.registerListener("test") { _, _ ->
            called++
        }
        postConfigChangedEvent()
        startComponentAndDependencies()

        virtualNodeListener?.onUpdate(
            setOf(holdingIdentity5),
            mapOf(
                holdingIdentity5 to VirtualNodeInfo(
                    holdingIdentity5,
                    cpiIdentifier5,
                    null,
                    UUID(0, 0),
                    null,
                    UUID(0, 0),
                    null,
                    UUID(0, 0),
                    timestamp = Instant.ofEpochSecond(100)
                )
            )
        )

        assertThat(called).isZero
    }

    @Test
    fun `registerListener will call the call back when new MGM is created`() {
        val processor = argumentCaptor<FinishedRegistrationsProcessor>()
        whenever(subscriptionFactory.createCompactedSubscription(any(), processor.capture(), any())).doReturn(subscription)
        var holdingIdentity: HoldingIdentity? = null
        var groupPolicy: GroupPolicy? = null
        groupPolicyProvider.registerListener("test") { id, gp ->
            holdingIdentity = id
            groupPolicy = gp
        }
        postConfigChangedEvent()
        startComponentAndDependencies()

        processor.firstValue.onNext(
            Record("", "", validPersistentMemberInfo),
            null,
            emptyMap()
        )

        assertThat(holdingIdentity).isEqualTo(holdingIdentity5)
        assertThat(groupPolicy).isEqualTo(parsedMgmGroupPolicy)
    }

    @Test
    fun `registerListener will call the call back when a snapshot is received`() {
        val processor = argumentCaptor<FinishedRegistrationsProcessor>()
        whenever(subscriptionFactory.createCompactedSubscription(any(), processor.capture(), any())).doReturn(subscription)
        var holdingIdentity: HoldingIdentity? = null
        var groupPolicy: GroupPolicy? = null
        groupPolicyProvider.registerListener("test") { id, gp ->
            holdingIdentity = id
            groupPolicy = gp
        }
        postConfigChangedEvent()
        startComponentAndDependencies()

        processor.firstValue.onSnapshot(
            mapOf("" to validPersistentMemberInfo)
        )

        assertThat(holdingIdentity).isEqualTo(holdingIdentity5)
        assertThat(groupPolicy).isEqualTo(parsedMgmGroupPolicy)
    }

    @Test
    fun `registerListener will not call when the data was not persisted`() {
        val processor = argumentCaptor<FinishedRegistrationsProcessor>()
        whenever(subscriptionFactory.createCompactedSubscription(any(), processor.capture(), any())).doReturn(subscription)
        var called = 0
        groupPolicyProvider.registerListener("test") { _, _ ->
            called++
        }
        postConfigChangedEvent()
        startComponentAndDependencies()

        processor.firstValue.onNext(
            Record(
                "",
                "",
                PersistentMemberInfo(
                    holdingIdentity2.toAvro(),
                    KeyValuePairList(
                        listOf(
                            KeyValuePair(
                                MemberInfoExtension.PARTY_NAME,
                                holdingIdentity2.x500Name.toString(),
                            ),
                        ),
                    ),
                    KeyValuePairList(
                        listOf(
                            KeyValuePair(
                                MemberInfoExtension.IS_MGM,
                                "true",
                            ),
                            KeyValuePair(
                                MemberInfoExtension.STATUS,
                                MemberInfoExtension.MEMBER_STATUS_ACTIVE,
                            ),
                        ),
                    ),
                )
            ),
            null,
            emptyMap()
        )

        assertThat(called).isZero
    }

    @Test
    fun `registerListener will not call when the member is not the viewwing member`() {
        val processor = argumentCaptor<FinishedRegistrationsProcessor>()
        whenever(subscriptionFactory.createCompactedSubscription(any(), processor.capture(), any())).doReturn(subscription)
        var called = 0
        groupPolicyProvider.registerListener("test") { _, _ ->
            called++
        }
        postConfigChangedEvent()
        startComponentAndDependencies()

        processor.firstValue.onNext(
            Record(
                "",
                "",
                PersistentMemberInfo(
                    holdingIdentity5.toAvro(),
                    KeyValuePairList(
                        listOf(
                            KeyValuePair(
                                MemberInfoExtension.PARTY_NAME,
                                holdingIdentity2.x500Name.toString(),
                            ),
                        ),
                    ),
                    KeyValuePairList(
                        listOf(
                            KeyValuePair(
                                MemberInfoExtension.IS_MGM,
                                "true",
                            ),
                            KeyValuePair(
                                MemberInfoExtension.STATUS,
                                MemberInfoExtension.MEMBER_STATUS_ACTIVE,
                            ),
                        ),
                    ),
                )
            ),
            null,
            emptyMap()
        )

        assertThat(called).isZero
    }

    @Test
    fun `registerListener will not call when the member is not an MGM`() {
        val processor = argumentCaptor<FinishedRegistrationsProcessor>()
        whenever(subscriptionFactory.createCompactedSubscription(any(), processor.capture(), any())).doReturn(subscription)
        var called = 0
        groupPolicyProvider.registerListener("test") { _, _ ->
            called++
        }
        postConfigChangedEvent()
        startComponentAndDependencies()

        processor.firstValue.onNext(
            Record(
                "",
                "",
                PersistentMemberInfo(
                    holdingIdentity5.toAvro(),
                    KeyValuePairList(
                        listOf(
                            KeyValuePair(
                                MemberInfoExtension.PARTY_NAME,
                                holdingIdentity5.x500Name.toString(),
                            ),
                        ),
                    ),
                    KeyValuePairList(
                        listOf(
                            KeyValuePair(
                                MemberInfoExtension.IS_MGM,
                                "false",
                            ),
                            KeyValuePair(
                                MemberInfoExtension.STATUS,
                                MemberInfoExtension.MEMBER_STATUS_ACTIVE,
                            ),
                        ),
                    ),
                )
            ),
            null,
            emptyMap()
        )

        assertThat(called).isZero
    }

    @Test
    fun `registerListener will not call when the member is not active`() {
        val processor = argumentCaptor<FinishedRegistrationsProcessor>()
        whenever(subscriptionFactory.createCompactedSubscription(any(), processor.capture(), any())).doReturn(subscription)
        var called = 0
        groupPolicyProvider.registerListener("test") { _, _ ->
            called++
        }
        postConfigChangedEvent()
        startComponentAndDependencies()

        processor.firstValue.onNext(
            Record(
                "",
                "",
                PersistentMemberInfo(
                    holdingIdentity5.toAvro(),
                    KeyValuePairList(
                        listOf(
                            KeyValuePair(
                                MemberInfoExtension.PARTY_NAME,
                                holdingIdentity5.x500Name.toString(),
                            ),
                        ),
                    ),
                    KeyValuePairList(
                        listOf(
                            KeyValuePair(
                                MemberInfoExtension.IS_MGM,
                                "true",
                            ),
                            KeyValuePair(
                                MemberInfoExtension.STATUS,
                                MemberInfoExtension.MEMBER_STATUS_PENDING,
                            ),
                        ),
                    ),
                )
            ),
            null,
            emptyMap()
        )

        assertThat(called).isZero
    }
}
