package net.corda.flow.rest.impl

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.flow.FlowKey
import net.corda.data.flow.output.FlowStatus
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.rest.FlowStatusCacheService
import net.corda.flow.rest.flowstatus.FlowStatusUpdateListener
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.Flow.FLOW_STATUS_TOPIC
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.time.Instant

@Component(service = [FlowStatusCacheService::class])
class DurableFlowStatusCacheServiceImpl @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CordaAvroSerializationFactory::class)
    private val cordaSerializationFactory: CordaAvroSerializationFactory,
) : FlowStatusCacheService, DurableProcessor<FlowKey, FlowStatus> {
    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val GROUP_NAME = "Flow Status Subscription"
    }

    override val keyClass: Class<FlowKey> get() = FlowKey::class.java
    override val valueClass: Class<FlowStatus> get() = FlowStatus::class.java

    private val lifecycleCoordinator = coordinatorFactory.createCoordinator<FlowStatusCacheService>(::eventHandler)
    private var subReg: RegistrationHandle? = null

    private var flowStatusSubscription: Subscription<FlowKey, FlowStatus>? = null
    private val stateManager = getMockStateManager()

    private val serializer = cordaSerializationFactory.createAvroSerializer<FlowStatus> {}

    override fun start() = lifecycleCoordinator.start()
    override fun stop() = lifecycleCoordinator.stop()
    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning


    override fun initialise(config: SmartConfig) {
        subReg?.close()
        flowStatusSubscription?.close()

        flowStatusSubscription = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig(GROUP_NAME, FLOW_STATUS_TOPIC),
            this,
            config,
            null
        )

        subReg = lifecycleCoordinator.followStatusChangesByName(
            setOf(flowStatusSubscription!!.subscriptionName)
        )
    }

    override fun onNext(events: List<Record<FlowKey, FlowStatus>>): List<Record<*, *>> {
        val flowKeys = events.map { it.key.toString() }
        val existingKeys = stateManager.get(flowKeys).keys.toSet()

        val (updatedStates, newStates) = events.mapNotNull { record ->
            val key = record.key.toString()
            record.value?.let { value ->
                serializer.serialize(value)?.let { State(key, it) }
            }
        }.partition { it.key in existingKeys }

        stateManager.create(newStates)
        stateManager.update(updatedStates)

        return emptyList()
    }

    override fun getStatus(clientRequestId: String, holdingIdentity: HoldingIdentity): FlowStatus? {
        TODO("Not yet implemented")
    }

    override fun getStatusesPerIdentity(holdingIdentity: HoldingIdentity): List<FlowStatus> {
        TODO("Not yet implemented")
    }

    override fun registerFlowStatusListener(
        clientRequestId: String,
        holdingIdentity: HoldingIdentity,
        listener: FlowStatusUpdateListener
    ): AutoCloseable {
        TODO("Not yet implemented")
    }

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
            }

            is RegistrationStatusChangeEvent -> {
                if (event.registration == subReg && event.status == LifecycleStatus.DOWN) {
                    coordinator.updateStatus(LifecycleStatus.DOWN)
                }
            }
        }
    }

    // Temporary measure until we integrate the real state manager
    private fun getMockStateManager(): StateManager {
        return object : StateManager {
            private val stateStore = mutableMapOf<String, State>()
            override val name: LifecycleCoordinatorName
                get() = LifecycleCoordinatorName("MockStateManager")

            override fun create(states: Collection<State>): Set<String> {
                states.forEach { state ->
                    stateStore[state.key] = state.copy(modifiedTime = Instant.now())
                }

                return states.map { it.key }.toSet()
            }

            override fun get(keys: Collection<String>): Map<String, State> {
                return keys.mapNotNull { key -> stateStore[key]?.let { key to it } }.toMap()
            }

            override fun update(states: Collection<State>): Map<String, State?> {
                val successful = mutableMapOf<String, State>()
                states.forEach { state ->
                    stateStore[state.key]?.let {
                        val updatedState = state.copy(
                            version = it.version + 1,
                            modifiedTime = Instant.now()
                        )
                        stateStore[state.key] = updatedState
                        successful[state.key] = updatedState
                    }
                }
                return successful
            }

            override fun delete(states: Collection<State>): Map<String, State> {
                val deleted = mutableMapOf<String, State>()
                states.forEach { state ->
                    stateStore.remove(state.key)?.let { deleted[state.key] = it }
                }
                return deleted
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
