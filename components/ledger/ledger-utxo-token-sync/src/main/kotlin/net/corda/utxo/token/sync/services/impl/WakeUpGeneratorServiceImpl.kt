package net.corda.utxo.token.sync.services.impl

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.schema.configuration.ConfigKeys
import net.corda.utxo.token.sync.factories.MessagingRecordFactory
import net.corda.utxo.token.sync.services.WakeUpGeneratorService
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoListener
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import java.util.concurrent.atomic.AtomicBoolean

class WakeUpGeneratorServiceImpl(
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    private val messagingRecordFactory: MessagingRecordFactory,
    private val publisherFactory: PublisherFactory,
) : WakeUpGeneratorService, VirtualNodeInfoListener {
    companion object {
        private val logger = contextLogger()
    }

    private val isRegistered = AtomicBoolean(false)
    private var publisher: Publisher? = null

    override fun onConfigChange(config: Map<String, SmartConfig>) {
        logger.debug { "Config received '$config'" }
        publisher?.close()
        publisher = null
        publisher = publisherFactory.createPublisher(
            PublisherConfig("TokenCacheSyncService", true),
            config.getConfig(ConfigKeys.MESSAGING_CONFIG)
        )

        if (!isRegistered.getAndSet(true)) {
            virtualNodeInfoReadService.registerCallback(this)
        }
    }

    override fun onUpdate(changedKeys: Set<HoldingIdentity>, currentSnapshot: Map<HoldingIdentity, VirtualNodeInfo>) {
        logger.debug { "Holding IDs received '${changedKeys.map { it.x500Name }.joinToString()}'" }
        val wakeUpsToSend = changedKeys.map {
            messagingRecordFactory.createSyncWakeup(it)
        }

        publisher?.publish(wakeUpsToSend)
    }
}
