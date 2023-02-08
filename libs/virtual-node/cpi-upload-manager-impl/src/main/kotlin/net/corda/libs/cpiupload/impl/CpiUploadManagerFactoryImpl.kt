package net.corda.libs.cpiupload.impl

import net.corda.data.chunking.UploadStatus
import net.corda.data.chunking.UploadStatusKey
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
import net.corda.schema.configuration.MessagingConfig
import org.osgi.service.component.annotations.Component

@Suppress("UNUSED")
@Component(service = [CpiUploadManagerFactory::class])
class CpiUploadManagerFactoryImpl : CpiUploadManagerFactory {
    companion object {
        const val CPI_UPLOAD_GROUP = "cpi.uploader"
        const val CPI_UPLOAD_CLIENT_NAME = "$CPI_UPLOAD_GROUP.rest"
    }

    private val ackProcessor = UploadStatusProcessor()

    private fun createChunkPublisher(config: SmartConfig, publisherFactory: PublisherFactory): Publisher {
        val publisherConfig = PublisherConfig(CPI_UPLOAD_CLIENT_NAME)
        return publisherFactory.createPublisher(publisherConfig, config)
    }

    /**
     * @param config
     * @param subscriptionFactory used to create subscribers
     * @param statusTopic we read (subscribe) to this topic to receive [UploadStatus] messages
     */
    private fun createSubscriber(
        config: SmartConfig,
        subscriptionFactory: SubscriptionFactory,
        statusTopic: String
    ): CompactedSubscription<UploadStatusKey, UploadStatus> {
        return subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig(CPI_UPLOAD_GROUP, statusTopic),
            ackProcessor,
            config
        )
    }

    override fun create(
        messagingConfig: SmartConfig,
        publisherFactory: PublisherFactory,
        subscriptionFactory: SubscriptionFactory
    ): CpiUploadManager {
        val statusTopic = Schemas.VirtualNode.CPI_UPLOAD_STATUS_TOPIC
        val uploadTopic = Schemas.VirtualNode.CPI_UPLOAD_TOPIC
        val publisher = createChunkPublisher(messagingConfig, publisherFactory)
        val subscription = createSubscriber(messagingConfig, subscriptionFactory, statusTopic)

        subscription.start()
        publisher.start()

        return CpiUploadManagerImpl(
            uploadTopic,
            publisher,
            subscription,
            ackProcessor,
            messagingConfig.getInt(MessagingConfig.MAX_ALLOWED_MSG_SIZE))
    }
}
