package net.corda.flow.rpcops.impl

import java.util.Collections
import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowKey
import net.corda.data.flow.output.FlowStatus
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.rpcops.FlowStatusCacheService
import net.corda.flow.rpcops.flowstatus.FlowStatusUpdateHandler
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.Flow.Companion.FLOW_STATUS_TOPIC
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(immediate = true, service = [FlowStatusCacheService::class])
class FlowStatusCacheServiceImpl @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory
) : FlowStatusCacheService, CompactedProcessor<FlowKey, FlowStatus> {

    private companion object {
        val log = contextLogger()
    }

    private var flowStatusSubscription: CompactedSubscription<FlowKey, FlowStatus>? = null
    private val flowStatusUpdateHandlers = Collections.synchronizedMap(mutableMapOf<FlowKey, FlowStatusUpdateHandler>())
    private val cache = Collections.synchronizedMap(mutableMapOf<FlowKey, FlowStatus>())
    private var subReg: RegistrationHandle? = null
    private val lifecycleCoordinator = coordinatorFactory.createCoordinator<FlowStatusCacheService>(::eventHandler)

    override val keyClass: Class<FlowKey> get() = FlowKey::class.java

    override val valueClass: Class<FlowStatus> get() = FlowStatus::class.java

    override val isRunning = true

    override fun start() = lifecycleCoordinator.start()

    override fun stop() = lifecycleCoordinator.stop()

    override fun initialise(config: SmartConfig) {
        subReg?.close()
        flowStatusSubscription?.close()

        flowStatusSubscription = subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig(
                "Flow Status Subscription", FLOW_STATUS_TOPIC
            ),
            this,
            config
        )

        subReg = lifecycleCoordinator.followStatusChangesByName(setOf(flowStatusSubscription!!.subscriptionName))

        flowStatusSubscription?.start()
    }

    override fun getStatus(clientRequestId: String, holdingIdentity: HoldingIdentity): FlowStatus? {
        return cache[FlowKey(clientRequestId, holdingIdentity)]
    }

    override fun getStatusesPerIdentity(holdingIdentity: HoldingIdentity): List<FlowStatus> {
         return cache.entries.filter { it.key.identity == holdingIdentity }.map {
             it.value
         }
    }

    override fun registerFlowStatusFeed(
        clientRequestId: String,
        holdingIdentity: HoldingIdentity,
        flowStatusUpdateHandler: FlowStatusUpdateHandler
    ) {
        val flowKey = FlowKey(clientRequestId, holdingIdentity)

        val errors = validateRegistrationForFlowStatusUpdates(flowKey, holdingIdentity, clientRequestId)

        if (errors.isNotEmpty()) {
            flowStatusUpdateHandler.onError(errors)
            return
        }

        flowStatusUpdateHandlers[flowKey] = flowStatusUpdateHandler
    }

    override fun unregisterFlowStatusFeed(clientRequestId: String, holdingIdentity: HoldingIdentity) {
        log.info("Unregistering flow status feed ")
        val key = FlowKey(clientRequestId, holdingIdentity)
        flowStatusUpdateHandlers.remove(key)
    }

    private fun validateRegistrationForFlowStatusUpdates(
        flowKey: FlowKey,
        holdingIdentity: HoldingIdentity,
        clientRequestId: String
    ): List<String> {
        val errors = mutableListOf<String>()
        if (flowStatusUpdateHandlers[flowKey] != null) {
            errors.add(
                "Identity $holdingIdentity has already registered for flow status feed for request $clientRequestId."
            )
        }

        if (cache[flowKey] == null) {
            log.info("Registering for flow status updates for unstarted flow req $clientRequestId, holdingId $holdingIdentity.")
        }
        return errors
    }

    override fun onSnapshot(currentData: Map<FlowKey, FlowStatus>) {
        cache.clear()

        currentData
            .filter { it.value.initiatorType == FlowInitiatorType.RPC }
            .forEach { cache[it.key] = it.value }

        lifecycleCoordinator.postCustomEventToFollowers(CacheLoadCompleteEvent())
    }

    override fun onNext(
        newRecord: Record<FlowKey, FlowStatus>,
        oldValue: FlowStatus?,
        currentData: Map<FlowKey, FlowStatus>
    ) {
        if (newRecord.value == null) {
            cache.remove(newRecord.key)
            // delete any listeners for this key
            flowStatusUpdateHandlers[newRecord.key]?.close()
            flowStatusUpdateHandlers.remove(newRecord.key)
        } else {
            cache[newRecord.key] = newRecord.value
            // update any listeners for this key
            flowStatusUpdateHandlers[newRecord.key]?.onFlowStatusUpdate(newRecord.value!!)
        }
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