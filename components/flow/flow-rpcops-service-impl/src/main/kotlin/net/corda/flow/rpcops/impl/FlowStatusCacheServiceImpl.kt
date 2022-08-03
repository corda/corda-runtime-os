package net.corda.flow.rpcops.impl

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimap
import com.google.common.collect.Multimaps.synchronizedMultimap
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowKey
import net.corda.data.flow.output.FlowStatus
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.rpcops.FlowStatusCacheService
import net.corda.flow.rpcops.flowstatus.FlowStatusUpdateException
import net.corda.flow.rpcops.flowstatus.FlowStatusUpdateListener
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
import net.corda.virtualnode.toCorda
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
        const val MAX_WEBSOCKET_CONNECTIONS_PER_FLOW_KEY = 10
    }

    private var flowStatusSubscription: CompactedSubscription<FlowKey, FlowStatus>? = null
    private val cache = Collections.synchronizedMap(mutableMapOf<FlowKey, FlowStatus>())

    private val statusListenerByUuid = ConcurrentHashMap<UUID, FlowStatusUpdateListener>()
    private val statusListenerIdsPerFlowKey: Multimap<FlowKey, UUID> = synchronizedMultimap(ArrayListMultimap.create())

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
        val flowKey = newRecord.key
        if (newRecord.value == null) {
            cache.remove(flowKey)
            statusListenerIdsPerFlowKey[flowKey].map { id ->
                statusListenerByUuid[id]?.close("Flow status removed from cache.")
                statusListenerByUuid.remove(id)
            }
            statusListenerIdsPerFlowKey.removeAll(flowKey)
        } else {
            cache[flowKey] = newRecord.value
            updateAllStatusListenersForFlowKey(flowKey, newRecord.value!!)
        }
    }

    override fun registerFlowStatusListener(
        clientRequestId: String,
        holdingIdentity: HoldingIdentity,
        listener: FlowStatusUpdateListener
    ) {
        val flowKey = FlowKey(clientRequestId, holdingIdentity)
        val errors = mutableListOf<String>()

        validateHandlerNotAlreadyRegistered(listener.id, errors)
        validateMaxConnectionsPerFlowKey(flowKey, errors)

        if (errors.isNotEmpty()) {
            throw FlowStatusUpdateException("${errors.size} errors during registration for flow status updates.", errors)
        }

        statusListenerByUuid[listener.id] = listener
        statusListenerIdsPerFlowKey.put(flowKey, listener.id)

        log.info("Registered flow status listener ${listener.id} " +
                "(clientRequestId: $clientRequestId, holdingIdentity: ${holdingIdentity.toCorda().shortHash}). " +
                "Total number of open listeners: ${statusListenerByUuid.size}.")

        cache[flowKey]?.let { listener.updateReceived(it) }
    }

    override fun unregisterFlowStatusListener(listenerId: UUID) {
        statusListenerByUuid.remove(listenerId)?.let {
            log.info("Unregistered flow status listener: $listenerId. Total number of open listeners: ${statusListenerByUuid.size}.")
        }
    }

    private fun validateMaxConnectionsPerFlowKey(flowKey: FlowKey, errors: MutableList<String>) {
        val existingHandlers = statusListenerIdsPerFlowKey[flowKey]
        val handlersForRequestAndHoldingIdAlreadyExist = existingHandlers != null && existingHandlers.isNotEmpty()
        if (handlersForRequestAndHoldingIdAlreadyExist) {
            if (existingHandlers.size >= MAX_WEBSOCKET_CONNECTIONS_PER_FLOW_KEY) {
                errors.add("Max WebSocket connections per flowkey has been reached ($MAX_WEBSOCKET_CONNECTIONS_PER_FLOW_KEY).")
            }
        }
    }

    private fun validateHandlerNotAlreadyRegistered(handlerId: UUID, errors: MutableList<String>) {
        if (statusListenerByUuid[handlerId] != null) {
            errors.add("FlowStatusUpdateHandler with id $handlerId has already registered.")
        }
    }

    private fun updateAllStatusListenersForFlowKey(flowKey: FlowKey, flowStatus: FlowStatus) {
        statusListenerIdsPerFlowKey[flowKey].map { id ->
            statusListenerByUuid[id]?.updateReceived(flowStatus)
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