package net.corda.utxo.token.sync.services.impl

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.schema.configuration.ConfigKeys
import net.corda.utxo.token.sync.factories.MessagingRecordFactory
import net.corda.utxo.token.sync.services.WakeUpGeneratorService
import net.corda.virtualnode.read.VirtualNodeInfoReadService

class WakeUpGeneratorServiceImpl(
    private var virtualNodeInfoReadService: VirtualNodeInfoReadService,
    private val messagingRecordFactory: MessagingRecordFactory,
    private val publisherFactory: PublisherFactory,
) : WakeUpGeneratorService {

    private var isCompleted = false
    private var publisher: Publisher? = null

    override fun onConfigChange(config: Map<String, SmartConfig>) {
        publisher?.close()
        publisher = null
        publisher = publisherFactory.createPublisher(
            PublisherConfig("TokenCacheSyncService", true),
            config.getConfig(ConfigKeys.MESSAGING_CONFIG)
        )
    }

    override fun isWakeUpRequired(): Boolean {
        return !isCompleted
    }

    override fun generateWakeUpEvents() {
        if (isCompleted) {
            return
        }

        checkNotNull(publisher) { "Attempted to generate events before a publisher has been created." }

        val wakeUpsToSend = virtualNodeInfoReadService.getAll().map {
            messagingRecordFactory.createSyncWakeup(it.holdingIdentity)
        }

        publisher?.publish(wakeUpsToSend)

        isCompleted = true
    }
}
