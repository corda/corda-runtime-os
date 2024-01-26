package net.corda.flow.rest.impl

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.flow.FlowKey
import net.corda.data.flow.output.FlowStatus
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
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import org.assertj.core.api.Assertions
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

    private var eventHandler = mock<LifecycleEventHandler>()
    private val topicSubscription = mock<Subscription<FlowKey, FlowStatus>>()
    private val topicSubscriptionName = LifecycleCoordinatorName("Test Flow Status Subscription")
    private lateinit var flowStatusCacheService: DurableFlowStatusCacheServiceImpl
    private lateinit var stateManager: StateManager
    private val config = mock<SmartConfig> { whenever(it.getInt(INSTANCE_ID)).thenReturn(2) }

    @BeforeEach
    fun setup() {
        whenever(lifecycleCoordinator.followStatusChangesByName(any())).thenReturn(lifecycleEventRegistration)

        whenever(topicSubscription.subscriptionName).thenReturn(topicSubscriptionName)
        whenever(subscriptionFactory.createDurableSubscription<FlowKey, FlowStatus>(any(), any(), any(), anyOrNull()))
            .thenReturn(topicSubscription)

        eventHandler = lifecycleTestContext.getEventHandler()
        stateManager = getMockStateManager()

        flowStatusCacheService = DurableFlowStatusCacheServiceImpl(
            subscriptionFactory,
            lifecycleTestContext.lifecycleCoordinatorFactory,
            mock<CordaAvroSerializationFactory>(),
            stateManager
        )
    }

    @Test
    fun `Test compacted subscription key class is flow status key`() {
        Assertions.assertThat(flowStatusCacheService.keyClass).isEqualTo(FlowKey::class.java)
    }

    @Test
    fun `Test compacted subscription value class is flow status`() {
        Assertions.assertThat(flowStatusCacheService.valueClass).isEqualTo(FlowStatus::class.java)
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

    private fun getMockStateManager(): StateManager {
        return object : StateManager {
            private val stateStore = mutableMapOf<String, State>()
            override val name: LifecycleCoordinatorName
                get() = LifecycleCoordinatorName("MockStateManager")

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
