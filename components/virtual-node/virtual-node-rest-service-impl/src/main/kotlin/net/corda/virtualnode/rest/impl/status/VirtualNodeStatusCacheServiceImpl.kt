package net.corda.virtualnode.rest.impl.status

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
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.VirtualNode.VIRTUAL_NODE_OPERATION_STATUS_TOPIC
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import net.corda.data.virtualnode.VirtualNodeOperationStatus as AvroVirtualNodeOperationStatus

@Suppress("Unused")
@Component(service = [VirtualNodeStatusCacheService::class])
class VirtualNodeStatusCacheServiceImpl @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory
) : VirtualNodeStatusCacheService, CompactedProcessor<String, AvroVirtualNodeOperationStatus> {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private var vNodeStatusSubscription: CompactedSubscription<String, AvroVirtualNodeOperationStatus>? = null
    private val cache = ConcurrentHashMap<String, AvroVirtualNodeOperationStatus>()
    private var subReg: RegistrationHandle? = null
    private val lifecycleCoordinator =
        coordinatorFactory.createCoordinator<VirtualNodeStatusCacheService>(::eventHandler)
    private var publisher: Publisher? = null

    override val keyClass: Class<String> get() = String::class.java

    override val valueClass: Class<AvroVirtualNodeOperationStatus> get() = AvroVirtualNodeOperationStatus::class.java

    override val isRunning = true

    override fun start() = lifecycleCoordinator.start()

    override fun stop() = lifecycleCoordinator.stop()

    override fun onConfiguration(config: SmartConfig) {
        subReg?.close()
        vNodeStatusSubscription?.close()
        publisher?.close()
        publisher = publisherFactory.createPublisher(
            PublisherConfig("VIRTUAL_NODE_STATUS_CACHE", true),
            config
        )

        vNodeStatusSubscription = subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig(
                "Virtual Node Status Subscription",
                VIRTUAL_NODE_OPERATION_STATUS_TOPIC
            ),
            this,
            config
        )

        subReg = lifecycleCoordinator.followStatusChangesByName(setOf(vNodeStatusSubscription!!.subscriptionName))

        vNodeStatusSubscription?.start()
    }

    override fun getStatus(requestId: String): AvroVirtualNodeOperationStatus? {
        return cache[requestId]
    }

    override fun setStatus(requestId: String, newStatus: AvroVirtualNodeOperationStatus) {
        cache[requestId] = newStatus
        val record = Record(
            VIRTUAL_NODE_OPERATION_STATUS_TOPIC,
            requestId,
            newStatus
        )

        publisher!!.publish(listOf(record))
    }

    override fun onSnapshot(currentData: Map<String, AvroVirtualNodeOperationStatus>) {
        cache.clear()

        currentData
            .forEach { cache[it.key] = it.value }

        lifecycleCoordinator.postCustomEventToFollowers(CacheLoadCompleteEvent())
    }

    override fun onNext(
        newRecord: Record<String, AvroVirtualNodeOperationStatus>,
        oldValue: AvroVirtualNodeOperationStatus?,
        currentData: Map<String, AvroVirtualNodeOperationStatus>
    ) {
        val key = newRecord.key
        val status = newRecord.value
        if (status == null) {
            cache.remove(key)
        } else {
            cache[key] = status
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
