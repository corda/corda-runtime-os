package net.corda.virtualnode.impl

import net.corda.configuration.read.ConfigKeys
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.schema.Schemas.Companion.VIRTUAL_NODE_INFO_TOPIC
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.VirtualNodeInfoListener
import net.corda.virtualnode.VirtualNodeInfoService
import net.corda.virtualnode.component.ServiceException
import net.corda.virtualnode.component.VirtualNodeInfoProcessor
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import java.util.Collections
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Virtual node info processor.
 *
 * This class listens and maintains a compacted queue of [HoldingIdentity] to [VirtualNodeInfo]
 *
 * It implements the [VirtualNodeInfoService] interface that calls back with updates as well as allows
 * the caller to directly query for [VirtualNodeInfo] by [HoldingIdentity] or 'short hash' of the [HoldingIdentity]
 * e.g. `1234ABCD`
 *
 * In this class [start()] can only be called successfully (and the `get` methods) when the `config` has been set.
 */
@Component(service = [VirtualNodeInfoProcessor::class])
class VirtualNodeInfoProcessorImpl @Activate constructor(
    @Reference
    private val subscriptionFactory: SubscriptionFactory
) : VirtualNodeInfoProcessor,
    CompactedProcessor<net.corda.data.identity.HoldingIdentity, net.corda.data.virtualnode.VirtualNodeInfo> {

    companion object {
        internal const val GROUP_NAME = "VIRTUAL_NODE_INFO_READER"

        val log: Logger = contextLogger()
    }

    /**
     * Holds all the virtual node info we receive off the wire.
     */
    private val virtualNodeInfoMap = VirtualNodeInfoMap()

    private var config: SmartConfig? = null

    @Volatile
    private var started = false

    @Volatile
    private var snapshotReceived = false

    private val lock = ReentrantLock()

    private var subscription: CompactedSubscription<net.corda.data.identity.HoldingIdentity, net.corda.data.virtualnode.VirtualNodeInfo>? =
        null

    private val listeners = Collections.synchronizedMap(mutableMapOf<ListenerSubscription, VirtualNodeInfoListener>())

    private val exceptionMessage = "Virtual Node Service not started because the config has not been set yet"

    //region Lifecycle

    override val isRunning: Boolean
        get() {
            return started
        }

    override fun start() {
        lock.withLock {
            if (subscription == null && config != null) {
                subscription =
                    subscriptionFactory.createCompactedSubscription(
                        SubscriptionConfig(GROUP_NAME, VIRTUAL_NODE_INFO_TOPIC),
                        this,
                        config!!
                    )
                subscription!!.start()
            }
            started = true
        }
    }

    override fun stop() {
        lock.withLock {
            if (started) {
                subscription?.stop()
                subscription = null
                started = false
                snapshotReceived = false
            }
        }
    }

    override fun close() {
        lock.withLock {
            stop()
            listeners.clear()
        }
    }

    //endregion

    //region CompactedProcessor

    override val keyClass: Class<net.corda.data.identity.HoldingIdentity>
        get() = net.corda.data.identity.HoldingIdentity::class.java

    override val valueClass: Class<net.corda.data.virtualnode.VirtualNodeInfo>
        get() = net.corda.data.virtualnode.VirtualNodeInfo::class.java

    override fun onSnapshot(currentData: Map<net.corda.data.identity.HoldingIdentity, net.corda.data.virtualnode.VirtualNodeInfo>) {
        virtualNodeInfoMap.putAll(
            currentData.mapKeys { VirtualNodeInfoMap.Key(it.key, it.key.toCorda().id) }
        )

        snapshotReceived = true

        val currentSnapshot = virtualNodeInfoMap.getAllAsCorda()
        listeners.forEach { it.value.onUpdate(currentSnapshot.keys, currentSnapshot) }
    }

    override fun onNext(
        newRecord: Record<net.corda.data.identity.HoldingIdentity, net.corda.data.virtualnode.VirtualNodeInfo>,
        oldValue: net.corda.data.virtualnode.VirtualNodeInfo?,
        currentData: Map<net.corda.data.identity.HoldingIdentity, net.corda.data.virtualnode.VirtualNodeInfo>
    ) {
        val key = VirtualNodeInfoMap.Key(newRecord.key, newRecord.key.toCorda().id)
        if (newRecord.value != null) {
            virtualNodeInfoMap.put(key, newRecord.value!!)
        } else {
            virtualNodeInfoMap.remove(key)
        }

        val currentSnapshot = virtualNodeInfoMap.getAllAsCorda()
        listeners.forEach { it.value.onUpdate(setOf(newRecord.key.toCorda()), currentSnapshot) }
    }

    //endregion

    //region VirtualNodeInfoService

    /**
     * May throw if the service component is has not started up yet.
     */
    override fun get(holdingIdentity: HoldingIdentity): VirtualNodeInfo? {
        if (config == null) throw ServiceException(exceptionMessage)

        return virtualNodeInfoMap.get(holdingIdentity.toAvro())?.toCorda()
    }

    /**
     * May throw if the service component is has not started up yet.
     */
    override fun getById(id: String): VirtualNodeInfo? {
        if (config == null) throw ServiceException(exceptionMessage)

        return virtualNodeInfoMap.getById(id)?.toCorda()
    }

    /**
     * Register the user's callback
     */
    override fun registerCallback(listener: VirtualNodeInfoListener): AutoCloseable {
        lock.withLock {
            val subscription = ListenerSubscription(this)
            listeners[subscription] = listener
            if (snapshotReceived) {
                val currentSnapshot = virtualNodeInfoMap.getAllAsCorda()
                listener.onUpdate(currentSnapshot.keys, currentSnapshot)
            }
            return subscription
        }
    }

    //endregion

    //region ConfigurationHandler

    /**
     * We only stop and (re) start the processor if the configuration we receive contains the correct
     * (kafka) messaging configuration
     */
    override fun onNewConfiguration(changedKeys: Set<String>, config: Map<String, SmartConfig>) {
        if (ConfigKeys.MESSAGING_KEY in changedKeys && config[ConfigKeys.MESSAGING_KEY] != null) {
            log.debug("Virtual Node Info Service received new messaging configuration, (re)starting internal services")
            stop()
            this.config = config[ConfigKeys.MESSAGING_KEY]!!
            start()
        }
    }

    //endregion


    /**
     * Unregister a caller's subscription when they close it.
     */
    private fun unregisterCallback(subscription: ListenerSubscription) {
        listeners.remove(subscription)
    }

    /**
     * We return this handle to the subscription callback to the caller so that the can [close()]
     * it and unregister if they wish.
     */
    private class ListenerSubscription(private val service: VirtualNodeInfoProcessorImpl) : AutoCloseable {
        override fun close() {
            service.unregisterCallback(this)
        }
    }
}
