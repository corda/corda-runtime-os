package net.corda.chunking.db.impl

import net.corda.chunking.RequestId
import net.corda.chunking.db.ChunkDbWriter
import net.corda.chunking.db.ChunkDbWriterFactory
import net.corda.chunking.db.ChunkWriteException
import net.corda.data.chunking.Chunk
import net.corda.libs.configuration.SmartConfig
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
    private val subscriptionFactory: SubscriptionFactory
) : ChunkDbWriterFactory {
    companion object {
        internal const val GROUP_NAME = "chunk.writer"
    }

    override fun create(
        config: SmartConfig,
        entityManagerFactory: EntityManagerFactory
    ): ChunkDbWriter {
        // Could be reused
        val uploadTopic = Schemas.VirtualNode.CPI_UPLOAD_TOPIC
        val statusTopic = Schemas.VirtualNode.CPI_UPLOAD_STATUS_TOPIC
        val subscription = createSubscription(uploadTopic, config, entityManagerFactory, statusTopic)
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
        statusTopic: String
    ): Subscription<RequestId, Chunk> {
        val queries = ChunkDbQueries(entityManagerFactory)
        // Processor writes chunks to the db *and* sends a status back to the caller on the topic.resp channel
        val processor = ChunkWriteToDbProcessor(statusTopic, queries)

        val instanceId = if (config.hasPath("instanceId")) config.getInt("instanceId") else 1
        val subscriptionConfig = SubscriptionConfig(GROUP_NAME, uploadTopic, instanceId)
        return try {
            subscriptionFactory.createDurableSubscription(subscriptionConfig, processor, config, null)
        } catch (e: Exception) {
             throw ChunkWriteException("Could not create subscription to process configuration update requests.", e)
        }
    }
}
