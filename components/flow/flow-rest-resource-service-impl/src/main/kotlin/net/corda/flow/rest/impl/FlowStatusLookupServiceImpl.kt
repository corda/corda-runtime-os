package net.corda.flow.rest.impl

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.flow.FlowKey
import net.corda.data.flow.output.FlowStatus
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.rest.FlowStatusCacheService
import net.corda.flow.rest.flowstatus.FlowStatusUpdateListener
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.StateManagerFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.Flow.FLOW_STATUS_TOPIC
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

    private val flowStatusCacheServiceImpl = FlowStatusCacheServiceImpl(subscriptionFactory, coordinatorFactory)

    private companion object {
        private const val GROUP_NAME = "flow_status_subscription"
    }

    private val lifecycleCoordinator = coordinatorFactory
        .createCoordinator<FlowStatusCacheService> { event, coordinator ->
            when (event) {
                is StartEvent -> coordinator.updateStatus(LifecycleStatus.UP)
            }
        }

    private var flowStatusSubscription: Subscription<FlowKey, FlowStatus>? = null

    private val serializer = cordaSerializationFactory.createAvroSerializer<Any> {}

    private var stateManager: StateManager? = null

    override fun start() = lifecycleCoordinator.start()
    override fun stop() = lifecycleCoordinator.stop()
    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning


    override fun initialise(config: SmartConfig) {
        val stateManagerConfig = config.getConfig(ConfigKeys.STATE_MANAGER_CONFIG)

        stateManager?.stop()
        val stateManagerNew = stateManagerFactory.create(stateManagerConfig, StateType.FLOW_STATUS).also { it.start() }
        stateManager = stateManagerNew
        flowStatusSubscription?.close()

        flowStatusSubscription = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig(GROUP_NAME, FLOW_STATUS_TOPIC),
            DurableFlowStatusProcessor(stateManagerNew, serializer),
            config,
            null
        ).also { it.start() }
    }

    override fun getStatus(clientRequestId: String, holdingIdentity: HoldingIdentity): FlowStatus? {
        return flowStatusCacheServiceImpl.getStatus(clientRequestId, holdingIdentity)
    }

    override fun getStatusesPerIdentity(holdingIdentity: HoldingIdentity): List<FlowStatus> {
        return flowStatusCacheServiceImpl.getStatusesPerIdentity(holdingIdentity)
    }

    override fun registerFlowStatusListener(
        clientRequestId: String,
        holdingIdentity: HoldingIdentity,
        listener: FlowStatusUpdateListener
    ): AutoCloseable {
        return registerFlowStatusListener(clientRequestId, holdingIdentity, listener)
    }
}
