package net.corda.chunking.db.impl

import net.corda.chunking.db.ChunkDbWriter
import net.corda.chunking.db.ChunkDbWriterFactory
import net.corda.chunking.db.ChunkWriteException
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.ChunkAck
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.config.RPCConfig
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
        internal const val GROUP_NAME = "chunk.writer"
        internal const val CLIENT_NAME_DB = "$GROUP_NAME.db"
        internal const val CLIENT_NAME_RPC = "$GROUP_NAME.rpc"
    }

    override fun create(
        config: SmartConfig,
        instanceId: Int,
        entityManagerFactory: EntityManagerFactory
    ): ChunkDbWriter {
        val publisher = createPublisher(config, instanceId)
        val subscription = createRPCSubscription(config, publisher, entityManagerFactory)
        return ChunkDbWriterImpl(subscription, publisher)
    }

    private fun createPublisher(config: SmartConfig, instanceId: Int): Publisher {
        val publisherConfig = PublisherConfig(CLIENT_NAME_DB, instanceId)
        return try {
            publisherFactory.createPublisher(publisherConfig, config)
        } catch (e: Exception) {
            throw ChunkWriteException("Could not create publisher to publish updated configuration.", e)
        }
    }

    private fun createRPCSubscription(
        config: SmartConfig,
        publisher: Publisher,
        entityManagerFactory: EntityManagerFactory
    ): RPCSubscription<Chunk, ChunkAck> {

        val rpcConfig = RPCConfig(
            GROUP_NAME,
            CLIENT_NAME_RPC,
            Schemas.VirtualNode.CPI_UPLOAD_TOPIC,
            Chunk::class.java,
            ChunkAck::class.java,
        )

        val queries = ChunkDbQueries(entityManagerFactory)
        val processor = ChunkWriteToDbProcessor(publisher, queries)

        return try {
            subscriptionFactory.createRPCSubscription(rpcConfig, config, processor)
        } catch (e: Exception) {
            throw ChunkWriteException("Could not create subscription to process configuration update requests.", e)
        }
    }
}
