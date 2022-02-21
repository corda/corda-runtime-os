package net.corda.chunking.db.impl

import net.corda.chunking.db.ChunkDbWriter
import net.corda.chunking.db.ChunkDbWriterFactory
import net.corda.chunking.db.ChunkWriteException
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.ChunkAck
import net.corda.libs.configuration.SmartConfig
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
    private val subscriptionFactory: SubscriptionFactory
) : ChunkDbWriterFactory {
    companion object {
        internal const val GROUP_NAME = "chunk.writer"
        internal const val CLIENT_NAME_DB = "$GROUP_NAME.db"
        internal const val CLIENT_NAME_RPC = "$GROUP_NAME.rpc"
    }

    override fun create(
        config: SmartConfig,
        entityManagerFactory: EntityManagerFactory
    ): ChunkDbWriter {
        val subscription = createRPCSubscription(config, entityManagerFactory)
        return ChunkDbWriterImpl(subscription)
    }

    private fun createRPCSubscription(
        config: SmartConfig,
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
        val processor = ChunkWriteToDbProcessor(queries)

        return try {
            subscriptionFactory.createRPCSubscription(rpcConfig, config, processor)
        } catch (e: Exception) {
            throw ChunkWriteException("Could not create subscription to process configuration update requests.", e)
        }
    }
}
