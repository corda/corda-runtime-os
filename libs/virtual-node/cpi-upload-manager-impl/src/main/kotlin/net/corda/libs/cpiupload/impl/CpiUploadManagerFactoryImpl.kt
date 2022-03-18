package net.corda.libs.cpiupload.impl

import net.corda.data.chunking.ChunkAckKey
import net.corda.data.chunking.ChunkAck
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.cpiupload.CpiUploadManager
import net.corda.libs.cpiupload.CpiUploadManagerFactory
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import org.osgi.service.component.annotations.Component

@Suppress("UNUSED")
@Component(service = [CpiUploadManagerFactory::class])
class CpiUploadManagerFactoryImpl : CpiUploadManagerFactory {
    companion object {
        const val CPI_UPLOAD_GROUP = "cpi.uploader"
        const val CPI_UPLOAD_CLIENT_NAME = "$CPI_UPLOAD_GROUP.rpc"
    }

    private val ackProcessor = UploadStatusProcessor()

    private fun createChunkPublisher(config: SmartConfig, publisherFactory: PublisherFactory): Publisher {
        val instanceId = null // explicitly state this is null - do we ever need to read this from config?
        val publisherConfig = PublisherConfig(CPI_UPLOAD_CLIENT_NAME, instanceId)
        return publisherFactory.createPublisher(publisherConfig, config)
    }

    /**
     * @param config
     * @param subscriptionFactory used to create subscribers
     * @param statusTopic we read (subscribe) to this topic to receive [ChunkAck] messages
     */
    private fun createSubscriber(
        config: SmartConfig,
        subscriptionFactory: SubscriptionFactory,
        statusTopic: String
    ): CompactedSubscription<ChunkAckKey, ChunkAck> {
        val instanceId = null // explicit rather than the 'hidden' default parameter fn call
        return subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig(CPI_UPLOAD_GROUP, statusTopic, instanceId),
            ackProcessor,
            config
        )
    }

    override fun create(
        config: SmartConfig,
        publisherFactory: PublisherFactory,
        subscriptionFactory: SubscriptionFactory
    ): CpiUploadManager {
        val statusTopic = Schemas.VirtualNode.CPI_UPLOAD_STATUS_TOPIC
        val uploadTopic = Schemas.VirtualNode.CPI_UPLOAD_TOPIC
        val publisher = createChunkPublisher(config, publisherFactory)
        val subscription = createSubscriber(config, subscriptionFactory, statusTopic)

        subscription.start()
        publisher.start()

        return CpiUploadManagerImpl(uploadTopic, publisher, subscription, ackProcessor)
    }
}
