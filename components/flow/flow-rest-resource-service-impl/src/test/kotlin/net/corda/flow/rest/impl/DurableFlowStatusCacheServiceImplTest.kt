package net.corda.flow.rest.impl

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowKey
import net.corda.data.flow.output.FlowStates
import net.corda.data.flow.output.FlowStatus
import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.schema.Schemas.Flow.FLOW_STATUS_TOPIC
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class DurableFlowStatusCacheServiceImplTest {
    private val subscriptionFactory = mock<SubscriptionFactory>()
    private val lifecycleTestContext = LifecycleTestContext()
    private val lifecycleCoordinator = lifecycleTestContext.lifecycleCoordinator
    private val lifecycleEventRegistration = mock<RegistrationHandle>()
    private val cordaSerializationFactory = mock<CordaAvroSerializationFactory>()
    private val serializer = mock<CordaAvroSerializer<FlowStatus>>()

    private var eventHandler = mock<LifecycleEventHandler>()
    private val topicSubscription = mock<Subscription<FlowKey, FlowStatus>>()
    private val topicSubscriptionName = LifecycleCoordinatorName("Test Flow Status Subscription")
    private lateinit var flowStatusCacheService: DurableFlowStatusCacheServiceImpl
    private lateinit var stateManager: StateManager
    private val config = mock<SmartConfig> { whenever(it.getInt(INSTANCE_ID)).thenReturn(2) }

    companion object {
        val FLOW_KEY_1 = FlowKey("a1", HoldingIdentity("b1", "c1"))
        val FLOW_KEY_2 = FlowKey("a2", HoldingIdentity("b1", "c1"))
    }

    @BeforeEach
    fun setup() {
        whenever(lifecycleCoordinator.followStatusChangesByName(any())).thenReturn(lifecycleEventRegistration)

        whenever(topicSubscription.subscriptionName).thenReturn(topicSubscriptionName)
        whenever(subscriptionFactory.createDurableSubscription<FlowKey, FlowStatus>(any(), any(), any(), anyOrNull()))
            .thenReturn(topicSubscription)

        whenever(serializer.serialize(any())).thenReturn("test".toByteArray())
        whenever(cordaSerializationFactory.createAvroSerializer<FlowStatus>(any())).thenReturn(serializer)

        stateManager = getMockStateManager()

        flowStatusCacheService = DurableFlowStatusCacheServiceImpl(
            subscriptionFactory,
            lifecycleTestContext.lifecycleCoordinatorFactory,
            cordaSerializationFactory,
            stateManager
        )

        eventHandler = lifecycleTestContext.getEventHandler()
    }

    @Test
    fun `Test compacted subscription key class is flow status key`() {
        assertThat(flowStatusCacheService.keyClass).isEqualTo(FlowKey::class.java)
    }

    @Test
    fun `Test compacted subscription value class is flow status`() {
        assertThat(flowStatusCacheService.valueClass).isEqualTo(FlowStatus::class.java)
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
            Schemas.Flow.FLOW_STATUS_TOPIC
        )

        verify(subscriptionFactory).createDurableSubscription(
            eq(expectedSubscriptionCfg),
            same(flowStatusCacheService),
            same(config),
            same(null)
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
        eventHandler.processEvent(RegistrationStatusChangeEvent(lifecycleEventRegistration, LifecycleStatus.DOWN), lifecycleCoordinator)
        verify(lifecycleCoordinator).updateStatus(LifecycleStatus.DOWN)
    }

    @Test
    fun `Test onNext creates a new record in the StateManager`() {
        val record = Record(FLOW_STATUS_TOPIC, FLOW_KEY_1, createFlowStatus())
        val key = FLOW_KEY_1.toString()

        flowStatusCacheService.initialise(config)
        flowStatusCacheService.onNext(listOf(record))

        val result = stateManager.get(setOf(key))

        assertThat(result.size).isEqualTo(1)
        assertThat(result.containsKey(key)).isTrue()
        assertThat(result[key]?.version).isEqualTo(0)
    }

    @Test
    fun `Test onNext creates multiple records in the StateManager on different keys`() {
        val record1 = Record(FLOW_STATUS_TOPIC, FLOW_KEY_1, createFlowStatus())
        val record2 = Record(FLOW_STATUS_TOPIC, FLOW_KEY_2, createFlowStatus())
        val key1 = FLOW_KEY_1.toString()
        val key2 = FLOW_KEY_2.toString()

        flowStatusCacheService.initialise(config)
        flowStatusCacheService.onNext(listOf(record1, record2))

        val result = stateManager.get(setOf(key1, key2))

        assertThat(result.size).isEqualTo(2)
        assertThat(result.containsKey(key1)).isTrue()
        assertThat(result.containsKey(key2)).isTrue()
        assertThat(result[key1]?.version).isEqualTo(0)
        assertThat(result[key2]?.version).isEqualTo(0)
    }

    @Test
    fun `Test onNext updates existing key if it already exists`() {
        val record1 = Record(FLOW_STATUS_TOPIC, FLOW_KEY_1, createFlowStatus(FlowStates.START_REQUESTED))
        val record2 = Record(FLOW_STATUS_TOPIC, FLOW_KEY_1, createFlowStatus(FlowStates.RUNNING))
        val key = FLOW_KEY_1.toString()

        flowStatusCacheService.initialise(config)
        flowStatusCacheService.onNext(listOf(record1))

        val result1 = stateManager.get(setOf(key))

        assertThat(result1.size).isEqualTo(1)
        assertThat(result1.containsKey(key)).isTrue()
        assertThat(result1[key]?.version).isEqualTo(0)

        flowStatusCacheService.onNext(listOf(record2))

        val result2 = stateManager.get(setOf(key))

        assertThat(result2.size).isEqualTo(1)
        assertThat(result2.containsKey(key)).isTrue()
        assertThat(result2[key]?.version).isEqualTo(1)
    }

    @Test
    fun `Test onNext processes a create and an update in a single call`() {
        val record1 = Record(FLOW_STATUS_TOPIC, FLOW_KEY_1, createFlowStatus(FlowStates.START_REQUESTED))
        val record2 = Record(FLOW_STATUS_TOPIC, FLOW_KEY_1, createFlowStatus(FlowStates.RUNNING))
        val record3 = Record(FLOW_STATUS_TOPIC, FLOW_KEY_2, createFlowStatus())

        val key1 = FLOW_KEY_1.toString()
        val key2 = FLOW_KEY_2.toString()

        // Persist our first record
        flowStatusCacheService.initialise(config)
        flowStatusCacheService.onNext(listOf(record1))

        val result1 = stateManager.get(setOf(key1))

        assertThat(result1.size).isEqualTo(1)
        assertThat(result1.containsKey(key1)).isTrue()
        assertThat(result1[key1]?.version).isEqualTo(0)

        // Persist a new record, and update our first
        flowStatusCacheService.onNext(listOf(record2, record3))

        val result2 = stateManager.get(setOf(key1, key2))

        assertThat(result2.size).isEqualTo(2)

        // Assert that our record on FLOW_KEY_1 has incremented its version
        assertThat(result2.containsKey(key1)).isTrue()
        assertThat(result2[key1]?.version).isEqualTo(1)

        // And that our record on FLOW_KEY_1 has been created
        assertThat(result2.containsKey(key2)).isTrue()
        assertThat(result2[key2]?.version).isEqualTo(0)
    }

    private fun createFlowStatus(flowStatus: FlowStates = FlowStates.START_REQUESTED) =
        FlowStatus().apply { initiatorType = FlowInitiatorType.RPC; this.flowStatus = flowStatus }

    private fun getMockStateManager(): StateManager {
        return object : StateManager {
            private val stateStore = mutableMapOf<String, State>()
            override val name = LifecycleCoordinatorName("MockStateManager")

            override fun create(states: Collection<State>): Set<String> {
                val failedKeys = mutableSetOf<String>()

                states.forEach { state ->
                    if (state.key in stateStore) {
                        failedKeys.add(state.key)
                    } else {
                        stateStore[state.key] = state.copy(modifiedTime = Instant.now())
                    }
                }

                return failedKeys
            }

            override fun get(keys: Collection<String>): Map<String, State> {
                return keys.mapNotNull { key -> stateStore[key]?.let { key to it } }.toMap()
            }

            override fun update(states: Collection<State>): Map<String, State?> {
                val failedUpdates = mutableMapOf<String, State?>()

                states.forEach { state ->
                    val currentState = stateStore[state.key]
                    if (currentState == null || currentState.version + 1 != state.version) {
                        // State does not exist or version mismatch
                        failedUpdates[state.key] = currentState
                    } else {
                        // Optimistic locking condition met
                        val updatedState = state.copy(modifiedTime = Instant.now())
                        stateStore[state.key] = updatedState
                    }
                }

                return failedUpdates
            }

            override fun delete(states: Collection<State>): Map<String, State> {
                val failedDeletion = mutableMapOf<String, State>()

                states.forEach { state ->
                    val currentState = stateStore[state.key]
                    if (currentState != null && currentState.version == state.version) {
                        stateStore.remove(state.key)
                    } else {
                        currentState?.let { failedDeletion[state.key] = currentState }
                    }
                }

                return failedDeletion
            }

            override fun updatedBetween(interval: IntervalFilter): Map<String, State> {
                TODO("Not yet implemented")
            }

            override fun findByMetadataMatchingAll(filters: Collection<MetadataFilter>): Map<String, State> {
                TODO("Not yet implemented")
            }

            override fun findByMetadataMatchingAny(filters: Collection<MetadataFilter>): Map<String, State> {
                TODO("Not yet implemented")
            }

            override fun findUpdatedBetweenWithMetadataMatchingAll(
                intervalFilter: IntervalFilter,
                metadataFilters: Collection<MetadataFilter>
            ): Map<String, State> {
                TODO("Not yet implemented")
            }

            override fun findUpdatedBetweenWithMetadataMatchingAny(
                intervalFilter: IntervalFilter,
                metadataFilters: Collection<MetadataFilter>
            ): Map<String, State> {
                TODO("Not yet implemented")
            }

            override val isRunning = true

            override fun start() {
                TODO("Not yet implemented")
            }

            override fun stop() {
                TODO("Not yet implemented")
            }
        }
    }
}
