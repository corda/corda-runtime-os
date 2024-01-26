package net.corda.flow.rest.impl

import com.google.common.collect.Multimap
import com.google.common.collect.Multimaps
import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowKey
import net.corda.data.flow.output.FlowStatus
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.rest.FlowStatusCacheService
import net.corda.flow.rest.flowstatus.FlowStatusUpdateListener
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.StateManagerFactory
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
import net.corda.schema.Schemas.Flow.FLOW_STATUS_TOPIC
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.StateManagerConfig.StateType.FLOW_STATUS
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

@Component(service = [FlowStatusCacheService::class])
class FlowStatusCacheServiceImpl @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = StateManagerFactory::class)
    private val stateManagerFactory: StateManagerFactory
) : FlowStatusCacheService, CompactedProcessor<FlowKey, FlowStatus> {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private var flowStatusSubscription: CompactedSubscription<FlowKey, FlowStatus>? = null
    private lateinit var cache: StateManager

    private val lock: ReadWriteLock = ReentrantReadWriteLock()
    private val statusListenersPerFlowKey: Multimap<FlowKey, FlowStatusUpdateListener> =
        Multimaps.newSetMultimap(ConcurrentHashMap()) { ConcurrentHashMap.newKeySet() }

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

        cache = stateManagerFactory.create(config.getConfig(ConfigKeys.STATE_MANAGER_CONFIG), FLOW_STATUS).also {
            it.start()
        }
    }

    override fun getStatus(clientRequestId: String, holdingIdentity: HoldingIdentity): FlowStatus? {
        return cache.get(listOf(FlowKey(clientRequestId, holdingIdentity).toString())).values.singleOrNull()?.let { (FlowStatus.fromByteBuffer(ByteBuffer.wrap(it.value))) }
    }

    override fun getStatusesPerIdentity(holdingIdentity: HoldingIdentity): List<FlowStatus> {
        TODO()
//        return cache.entries.filter { it.key.identity == holdingIdentity }.map {
//            it.value
//        }
    }

    override fun onSnapshot(currentData: Map<FlowKey, FlowStatus>) {
        val states = currentData
            .filter { it.value.initiatorType == FlowInitiatorType.RPC }
            .map { State(it.key.toString(), it.value.toByteBuffer().array()) }

        val failedStates = cache.create(states)

        assert(failedStates.isEmpty()) { "Failed to save states to state manager" }

        lifecycleCoordinator.postCustomEventToFollowers(CacheLoadCompleteEvent())
    }

    override fun onNext(
        newRecord: Record<FlowKey, FlowStatus>,
        oldValue: FlowStatus?,
        currentData: Map<FlowKey, FlowStatus>
    ) {
        try {
            val flowKey = newRecord.key
            val flowStatus = newRecord.value
            if (flowStatus == null) {
                cache.delete(cache.get(listOf(flowKey.toString())).values)
                lock.writeLock().withLock { statusListenersPerFlowKey.removeAll(flowKey) }.map {
                    it.close("Flow status removed from cache when null flow status received.")
                }
            } else {
                cache.update(cache.get(listOf(flowKey.toString()))
                    .map { (key, _) -> State(key, flowStatus.toByteBuffer().array()) })
                updateAllStatusListenersForFlowKey(flowKey, flowStatus)
            }
        } catch (ex: Exception) {
            log.error("Unhandled error when processing onNext for FlowStatus", ex)
        }
    }

    override fun registerFlowStatusListener(
        clientRequestId: String,
        holdingIdentity: HoldingIdentity,
        listener: FlowStatusUpdateListener
    ): AutoCloseable {
        val flowKey = FlowKey(clientRequestId, holdingIdentity)

        lock.writeLock().withLock {
            statusListenersPerFlowKey.put(flowKey, listener)
        }

        log.info(
            "Registered flow status listener ${listener.id} " +
                    "(clientRequestId: $clientRequestId, holdingIdentity: ${holdingIdentity.toCorda().shortHash}). " +
                    "Total number of open listeners: ${statusListenersPerFlowKey.size()}."
        )

        // If the status is already known for a particular flow - deliver it to the listener
        // This can be the case when flow is already completed.
        cache.get(listOf(flowKey.toString())).values.singleOrNull()?.let { listener.updateReceived(FlowStatus.fromByteBuffer(ByteBuffer.wrap(it.value))) }
        return AutoCloseable { unregisterFlowStatusListener(clientRequestId, holdingIdentity, listener) }
    }

    private fun unregisterFlowStatusListener(
        clientRequestId: String,
        holdingIdentity: HoldingIdentity,
        listener: FlowStatusUpdateListener
    ) {
        val removed = lock.writeLock()
            .withLock { statusListenersPerFlowKey[FlowKey(clientRequestId, holdingIdentity)].remove(listener) }
        if (removed) {
            log.info(
                "Unregistered flow status listener: ${listener.id} for clientId: $clientRequestId." +
                        " Total number of open listeners: ${statusListenersPerFlowKey.size()}."
            )
        }
    }

    private fun updateAllStatusListenersForFlowKey(flowKey: FlowKey, flowStatus: FlowStatus) {
        val flowStatusUpdateListeners = lock.readLock().withLock { LinkedList(statusListenersPerFlowKey[flowKey]) }
        flowStatusUpdateListeners.map { listener ->
            listener.updateReceived(flowStatus)
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