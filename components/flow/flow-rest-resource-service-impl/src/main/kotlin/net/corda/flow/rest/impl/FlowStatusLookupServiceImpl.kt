package net.corda.flow.rest.impl

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.flow.FlowKey
import net.corda.data.flow.output.FlowStatus
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.rest.FlowStatusCacheService
import net.corda.flow.rest.flowstatus.FlowStatusUpdateListener
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.Operation
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.StateManagerFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.Flow.FLOW_STATUS_TOPIC
import net.corda.schema.Schemas.Rest.REST_FLOW_STATUS_CLEANUP_TOPIC
import net.corda.schema.Schemas.ScheduledTask.SCHEDULED_TASK_TOPIC_FLOW_STATUS_PROCESSOR
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.StateManagerConfig.StateType
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowStatusCacheService::class])
class FlowStatusLookupServiceImpl @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CordaAvroSerializationFactory::class)
    private val cordaSerializationFactory: CordaAvroSerializationFactory,
    @Reference(service = StateManagerFactory::class)
    private val stateManagerFactory: StateManagerFactory,
) : FlowStatusCacheService {
    private val lifecycleCoordinator = coordinatorFactory
        .createCoordinator<FlowStatusCacheService> { event, coordinator ->
            when (event) {
                is StartEvent -> coordinator.updateStatus(LifecycleStatus.UP)
            }
        }

    private val serializer = cordaSerializationFactory.createAvroSerializer<Any> {}
    private val deSerializer = cordaSerializationFactory.createAvroDeserializer({}, FlowStatus::class.java)

    private var stateManager: StateManager? = null

    override fun start() = lifecycleCoordinator.start()
    override fun stop() = lifecycleCoordinator.stop()
    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

    override fun initialise(config: SmartConfig) {
        TODO("Not yet implemented")
    }


    override fun initialise(config: Map<String, SmartConfig>) {
        val messagingConfig = config.getConfig(ConfigKeys.MESSAGING_CONFIG)
        val stateManagerConfig = config.getConfig(ConfigKeys.STATE_MANAGER_CONFIG)
        val restConfig = config.getConfig(ConfigKeys.REST_CONFIG)

        stateManager?.stop()
        val stateManagerNew = stateManagerFactory.create(stateManagerConfig, StateType.FLOW_STATUS).also { it.start() }

        stateManager = stateManagerNew

        lifecycleCoordinator.createManagedResource("FLOW_STATUS_LOOKUP_SUBSCRIPTION") {
            subscriptionFactory.createDurableSubscription(
                SubscriptionConfig(
                    "flow.status.subscription",
                    FLOW_STATUS_TOPIC
                ),
                DurableFlowStatusProcessor(stateManagerNew, serializer),
                messagingConfig,
                null
            )
        }.start()

        lifecycleCoordinator.createManagedResource("FLOW_STATUS_CLEANUP_TASK_SUBSCRIPTION") {
            subscriptionFactory.createDurableSubscription(
                SubscriptionConfig(
                    "flow.status.cleanup.tasks",
                    SCHEDULED_TASK_TOPIC_FLOW_STATUS_PROCESSOR
                ),
                FlowStatusCleanupProcessor(restConfig, stateManagerNew),
                messagingConfig,
                null
            )
        }.start()

        lifecycleCoordinator.createManagedResource("FLOW_STATUS_DELETION_EXECUTOR_SUBSCRIPTION") {
            subscriptionFactory.createDurableSubscription(
                SubscriptionConfig(
                    "flow.status.cleanup.executor",
                    REST_FLOW_STATUS_CLEANUP_TOPIC
                ),
                FlowStatusDeletionExecutor(stateManagerNew),
                messagingConfig,
                null
            )
        }.start()
    }

    override fun getStatus(clientRequestId: String, holdingIdentity: HoldingIdentity): FlowStatus? {

        val flowKey = FlowKey(clientRequestId, holdingIdentity)
        val flowKeys = listOf(flowKey.toString())

        return requireNotNull(stateManager) { "stateManager is null" }
            .get(flowKeys)
            .asSequence()
            .map { it.value }
            .firstOrNull()
            ?.let { state ->
                deSerializer.deserialize(state.value)
            }
    }

    override fun getStatusesPerIdentity(holdingIdentity: HoldingIdentity): List<FlowStatus> {
        val filter = MetadataFilter(HOLDING_IDENTITY_METADATA_KEY, Operation.Equals, holdingIdentity.toString())

        return requireNotNull(stateManager) { "stateManager is null" }
            .findByMetadata(filter)
            .map { deSerializer.deserialize(it.value.value) }
            .filterNotNull()
    }

    override fun registerFlowStatusListener(
        clientRequestId: String,
        holdingIdentity: HoldingIdentity,
        listener: FlowStatusUpdateListener
    ): AutoCloseable {
        TODO("Not yet implemented")
    }
}
