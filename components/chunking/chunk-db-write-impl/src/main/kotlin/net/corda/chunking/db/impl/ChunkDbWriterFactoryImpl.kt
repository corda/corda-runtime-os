package net.corda.chunking.db.impl

import net.corda.chunking.RequestId
import net.corda.chunking.db.ChunkDbWriter
import net.corda.chunking.db.ChunkDbWriterFactory
import net.corda.chunking.db.ChunkWriteException
import net.corda.cpiinfo.write.CpiInfoWriteService
import net.corda.data.chunking.Chunk
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import javax.persistence.EntityManagerFactory

@Suppress("UNUSED")
@Component(service = [ChunkDbWriterFactory::class])
class ChunkDbWriterFactoryImpl @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory
) : ChunkDbWriterFactory {
    companion object {
        internal const val GROUP_NAME = "cpi.chunk.writer"
    }

    override fun create(
        config: SmartConfig,
        entityManagerFactory: EntityManagerFactory,
        cpiInfoWriteService: CpiInfoWriteService
    ): ChunkDbWriter {
        // Could be reused
        val uploadTopic = Schemas.VirtualNode.CPI_UPLOAD_TOPIC
        val statusTopic = Schemas.VirtualNode.CPI_UPLOAD_STATUS_TOPIC

        val subscription = createSubscription(
            uploadTopic,
            config,
            entityManagerFactory,
            statusTopic,
            cpiInfoWriteService
        )

        return ChunkDbWriterImpl(subscription)
    }

    /**
     * @param uploadTopic we read (subscribe) chunks from this topic
     * @param config
     * @param entityManagerFactory we use this to write chunks to the database
     * @param statusTopic we write (publish) status changes to this topic
     */
    private fun createSubscription(
        uploadTopic: String,
        config: SmartConfig,
        entityManagerFactory: EntityManagerFactory,
        statusTopic: String,
        cpiInfoWriteService: CpiInfoWriteService
    ): Subscription<RequestId, Chunk> {
        val queries = DatabaseQueries(entityManagerFactory)
        val validator = CpiValidatorImpl(queries, cpiInfoWriteService)
        val processor = ChunkWriteToDbProcessor(statusTopic, queries, validator)
        val subscriptionConfig = SubscriptionConfig(GROUP_NAME, uploadTopic)
        return try {
            subscriptionFactory.createDurableSubscription(subscriptionConfig, processor, config, null)
        } catch (e: Exception) {
             throw ChunkWriteException("Could not create subscription to process configuration update requests.", e)
        }
    }
}
