package net.corda.configuration.rpcops.impl.tests

import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowStatusKey
import net.corda.data.flow.output.FlowStatus
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.rpcops.impl.CacheLoadCompleteEvent
import net.corda.flow.rpcops.impl.FlowStatusCacheServiceImpl
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.Flow.Companion.FLOW_STATUS_TOPIC
import net.corda.schema.configuration.MessagingConfig.Boot.INSTANCE_ID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FlowStatusCacheServiceImplTest {
    private val subscriptionFactory = mock<SubscriptionFactory>()
    private val lifecycleTestContext = LifecycleTestContext()
    private val lifecycleCoordinator = lifecycleTestContext.lifecycleCoordinator
    private val lifecycleEventRegistration = mock<RegistrationHandle>()

    private var eventHandler = mock<LifecycleEventHandler>()
    private val topicSubscription = mock<CompactedSubscription<FlowStatusKey, FlowStatus>>()
    private val topicSubscriptionName = LifecycleCoordinatorName("c")
    private lateinit var flowStatusCacheService: FlowStatusCacheServiceImpl
    private val config = mock<SmartConfig> { whenever(it.getInt(INSTANCE_ID)).thenReturn(2) }

    @BeforeEach
    fun setup() {
        whenever(lifecycleCoordinator.followStatusChangesByName(any())).thenReturn(lifecycleEventRegistration)

        whenever(topicSubscription.subscriptionName).thenReturn(topicSubscriptionName)
        whenever(subscriptionFactory.createCompactedSubscription<FlowStatusKey, FlowStatus>(any(), any(), any()))
            .thenReturn(topicSubscription)

        flowStatusCacheService =
            FlowStatusCacheServiceImpl(subscriptionFactory, lifecycleTestContext.lifecycleCoordinatorFactory)

        eventHandler = lifecycleTestContext.getEventHandler()
    }

    @Test
    fun `Test compacted subscription key class is flow status key`() {
        assertThat(flowStatusCacheService.keyClass).isEqualTo(FlowStatusKey::class.java)
    }

    @Test
    fun `Test compacted subscription value class is flow status`() {
        assertThat(flowStatusCacheService.valueClass).isEqualTo(FlowStatus::class.java)
    }

    @Test
    fun `Test is running always retuns true`() {
        assertThat(flowStatusCacheService.isRunning).isTrue
    }

    @Test
    fun `Test start starts the lifecycle coordinator`() {
        flowStatusCacheService.start()
        verify(lifecycleCoordinator).start()
    }

    @Test
    fun `Test stop stops the lifecycle coordinator`() {
        flowStatusCacheService.stop()
        verify(lifecycleCoordinator).stop()
    }

    @Test
    fun `Test initialise creates new topic subscription and starts it`() {
        flowStatusCacheService.initialise(config)

        val expectedSubscriptionCfg = SubscriptionConfig(
            "Flow Status Subscription",
            FLOW_STATUS_TOPIC
        )

        verify(subscriptionFactory).createCompactedSubscription(
            eq(expectedSubscriptionCfg),
            same(flowStatusCacheService),
            same(config)
        )

        verify(topicSubscription).start()
    }

    @Test
    fun `Test initialise closes any existing topic subscription`() {
        flowStatusCacheService.initialise(config)
        // second time around we close the existing subscription
        flowStatusCacheService.initialise(config)
        verify(topicSubscription).close()
    }

    @Test
    fun `Test initialise registers to receive topic subscription lifecycle events`() {
        flowStatusCacheService.initialise(config)

        verify(lifecycleCoordinator).followStatusChangesByName(eq(setOf(topicSubscriptionName)))
    }

    @Test
    fun `Test initialise closes any existing lifecycle registration handle`() {
        flowStatusCacheService.initialise(config)
        // second time around we close the existing subscription
        flowStatusCacheService.initialise(config)
        verify(lifecycleEventRegistration).close()
    }

    @Test
    fun `Test on start event component status up is signaled`() {
        eventHandler.processEvent(StartEvent(), lifecycleCoordinator)
        verify(lifecycleCoordinator).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `Test on topic subscription going down component status down is signaled`() {
        flowStatusCacheService.initialise(config)
        eventHandler.processEvent(RegistrationStatusChangeEvent(lifecycleEventRegistration,LifecycleStatus.DOWN), lifecycleCoordinator)
        verify(lifecycleCoordinator).updateStatus(LifecycleStatus.DOWN)
    }

    @Test
    fun `Test on subscription snapshot cache is updated with only RPC statuses`() {
        val key1 = FlowStatusKey("a1", HoldingIdentity("b1", "c1"))
        val key2 = FlowStatusKey("a2", HoldingIdentity("b2", "c2"))

        val value1 = FlowStatus().apply { initiatorType = FlowInitiatorType.RPC }
        val value2 = FlowStatus().apply { initiatorType = FlowInitiatorType.P2P }

        assertThat(flowStatusCacheService.getStatus("a1", key1.identity)).isNull()
        assertThat(flowStatusCacheService.getStatus("a2", key2.identity)).isNull()

        flowStatusCacheService.onSnapshot(mapOf(key1 to value1, key2 to value2))
        assertThat(flowStatusCacheService.getStatus("a1", key1.identity)).isSameAs(value1)
        assertThat(flowStatusCacheService.getStatus("a2", key2.identity)).isNull()
    }

    @Test
    fun `Test on subscription snapshot cache load complete event is posted`() {
        val key = FlowStatusKey("a1", HoldingIdentity("b1", "c1"))
        val value = FlowStatus().apply { initiatorType = FlowInitiatorType.RPC }

        flowStatusCacheService.onSnapshot(mapOf(key to value))

        verify(lifecycleCoordinator).postEvent(isA<CacheLoadCompleteEvent>())
    }

    @Test
    fun `Test on next inserts new item`() {
        val key = FlowStatusKey("a1", HoldingIdentity("b1", "c1"))
        val value1 = FlowStatus().apply { initiatorType = FlowInitiatorType.RPC }

        flowStatusCacheService.onSnapshot(mapOf())
        assertThat(flowStatusCacheService.getStatus("a1", key.identity)).isNull()
        flowStatusCacheService.onNext(Record("",key,value1), null, mapOf())
        assertThat(flowStatusCacheService.getStatus("a1", key.identity)).isSameAs(value1)
    }

    @Test
    fun `Test on next deletes removed item`() {
        val key1 = FlowStatusKey("a1", HoldingIdentity("b1", "c1"))
        val key2 = FlowStatusKey("a2", HoldingIdentity("b2", "c2"))

        val value1 = FlowStatus().apply { initiatorType = FlowInitiatorType.RPC }
        val value2 = FlowStatus().apply { initiatorType = FlowInitiatorType.RPC }

        flowStatusCacheService.onSnapshot(mapOf(key1 to value1, key2 to value2))

        assertThat(flowStatusCacheService.getStatus("a1", key1.identity)).isSameAs(value1)
        flowStatusCacheService.onNext(Record("",key1,null), value1, mapOf())
        assertThat(flowStatusCacheService.getStatus("a1", key1.identity)).isNull()
    }

    @Test
    fun `Test on next updates existing item`() {
        val key = FlowStatusKey("a1", HoldingIdentity("b1", "c1"))
        val value1 = FlowStatus().apply { initiatorType = FlowInitiatorType.RPC }
        val value2 = FlowStatus().apply { initiatorType = FlowInitiatorType.RPC }

        flowStatusCacheService.onSnapshot(mapOf(key to value1))
        assertThat(flowStatusCacheService.getStatus("a1", key.identity)).isSameAs(value1)
        flowStatusCacheService.onNext(Record("",key,value2), value1, mapOf())
        assertThat(flowStatusCacheService.getStatus("a1", key.identity)).isSameAs(value2)
    }

}