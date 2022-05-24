package net.corda.chunking.db.impl

import javax.persistence.EntityManagerFactory
import net.corda.chunking.RequestId
import net.corda.chunking.db.ChunkDbWriter
import net.corda.chunking.db.ChunkDbWriterFactory
import net.corda.chunking.db.ChunkWriteException
import net.corda.chunking.db.impl.persistence.DatabaseChunkPersistence
import net.corda.chunking.db.impl.persistence.StatusPublisher
import net.corda.chunking.db.impl.validation.CpiValidatorImpl
import net.corda.cpiinfo.write.CpiInfoWriteService
import net.corda.data.chunking.Chunk
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.utilities.PathProvider
import net.corda.utilities.TempPathProvider
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("UNUSED")
@Component(service = [ChunkDbWriterFactory::class])
class ChunkDbWriterFactoryImpl(
    private val subscriptionFactory: SubscriptionFactory,
    private val publisherFactory: PublisherFactory,
    private val tempPathProvider: PathProvider
) : ChunkDbWriterFactory {

    @Activate
    constructor(
        @Reference(service = SubscriptionFactory::class)
        subscriptionFactory: SubscriptionFactory,
        @Reference(service = PublisherFactory::class)
        publisherFactory: PublisherFactory
    ) : this(subscriptionFactory, publisherFactory, TempPathProvider())

    companion object {
        internal const val GROUP_NAME = "cpi.chunk.writer"
        internal const val CLIENT_NAME = "chunk-writer"

        const val CPI_CACHE_DIR = "cpi-cache"
        const val CPI_PARTS_DIR = "cpi-parts"
    }

    override fun create(
        messagingConfig: SmartConfig,
        bootConfig: SmartConfig,
        entityManagerFactory: EntityManagerFactory,
        cpiInfoWriteService: CpiInfoWriteService
    ): ChunkDbWriter {
        // Could be reused
        val uploadTopic = Schemas.VirtualNode.CPI_UPLOAD_TOPIC
        val statusTopic = Schemas.VirtualNode.CPI_UPLOAD_STATUS_TOPIC

        val subscription = createSubscription(
            uploadTopic,
            messagingConfig,
            bootConfig,
            entityManagerFactory,
            statusTopic,
            cpiInfoWriteService
        )

        return ChunkDbWriterImpl(subscription)
    }

    private fun createPublisher(config: SmartConfig): Publisher {
        val publisherConfig = PublisherConfig(CLIENT_NAME)
        return publisherFactory.createPublisher(publisherConfig, config)
    }

    /**
     * @param uploadTopic we read (subscribe) chunks from this topic
     * @param messagingConfig
     * @param entityManagerFactory we use this to write chunks to the database
     * @param statusTopic we write (publish) status changes to this topic
     */
    @Suppress("LongParameterList")
    private fun createSubscription(
        uploadTopic: String,
        messagingConfig: SmartConfig,
        bootConfig: SmartConfig,
        entityManagerFactory: EntityManagerFactory,
        statusTopic: String,
        cpiInfoWriteService: CpiInfoWriteService
    ): Subscription<RequestId, Chunk> {
        val persistence = DatabaseChunkPersistence(entityManagerFactory)
        val publisher = createPublisher(messagingConfig)
        val statusPublisher = StatusPublisher(statusTopic, publisher)
        val cpiCacheDir = tempPathProvider.getOrCreate(bootConfig, CPI_CACHE_DIR)
        val cpiPartsDir = tempPathProvider.getOrCreate(bootConfig, CPI_PARTS_DIR)
        val validator =
            CpiValidatorImpl(statusPublisher, persistence, cpiInfoWriteService, cpiCacheDir, cpiPartsDir)
        val processor = ChunkWriteToDbProcessor(statusPublisher, persistence, validator)
        val subscriptionConfig = SubscriptionConfig(GROUP_NAME, uploadTopic)
        return try {
            subscriptionFactory.createDurableSubscription(subscriptionConfig, processor, messagingConfig, null)
        } catch (e: Exception) {
             throw ChunkWriteException("Could not create subscription to process configuration update requests.", e)
        }
    }
}
