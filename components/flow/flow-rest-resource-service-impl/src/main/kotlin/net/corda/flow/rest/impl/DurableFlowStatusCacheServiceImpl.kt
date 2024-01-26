package net.corda.flow.rest.impl

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.flow.FlowKey
import net.corda.data.flow.output.FlowStatus
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.rest.FlowStatusCacheService
import net.corda.flow.rest.flowstatus.FlowStatusUpdateListener
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
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

@Component(service = [FlowStatusCacheService::class])
class DurableFlowStatusCacheServiceImpl @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CordaAvroSerializationFactory::class)
    private val cordaSerializationFactory: CordaAvroSerializationFactory,
    private val stateManager: StateManager
) : FlowStatusCacheService, DurableProcessor<FlowKey, FlowStatus> {

    private companion object {
        private const val GROUP_NAME = "Flow Status Subscription"
    }

    override val keyClass: Class<FlowKey> get() = FlowKey::class.java
    override val valueClass: Class<FlowStatus> get() = FlowStatus::class.java

    private val lifecycleCoordinator = coordinatorFactory.createCoordinator<FlowStatusCacheService>(::eventHandler)
    private var subReg: RegistrationHandle? = null

    private var flowStatusSubscription: Subscription<FlowKey, FlowStatus>? = null

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

        flowStatusSubscription?.start()
    }

    override fun onNext(events: List<Record<FlowKey, FlowStatus>>): List<Record<*, *>> {
        val flowKeys = events.map { it.key.toString() }
        val existingStates = stateManager.get(flowKeys)
        val existingKeys = existingStates.keys.toSet()

        val (updatedStates, newStates) = events.mapNotNull { record ->
            val key = record.key.toString()
            val bytes = record.value?.let { serializer.serialize(it) } ?: return@mapNotNull null

            existingStates[key]
                ?.let { oldState -> oldState.copy(value = bytes, version = oldState.version + 1) }
                ?: State(key, bytes)

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
}
