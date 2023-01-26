package net.corda.messaging.chunking

import java.util.function.Consumer
import net.corda.chunking.ChunkBuilderService
import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializer
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.chunking.ConsumerChunkService
import net.corda.messaging.api.chunking.MessagingChunkFactory
import net.corda.messaging.api.chunking.ProducerChunkService
import net.corda.schema.configuration.MessagingConfig
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [MessagingChunkFactory::class])
class MessagingChunkFactoryImpl @Activate constructor(
    @Reference(service = ChunkBuilderService::class)
    private val chunkBuilderService: ChunkBuilderService,
) : MessagingChunkFactory {

    override fun <K : Any, V : Any> createConsumerChunkService(
        keyDeserializer: CordaAvroDeserializer<K>,
        valueDeserializer: CordaAvroDeserializer<V>,
        onError: Consumer<ByteArray>,
        ): ConsumerChunkService<K, V> {
        return ConsumerChunkServiceImpl(keyDeserializer, valueDeserializer, onError)
    }

    override fun createProducerChunkService(config: SmartConfig, cordaAvroSerializer: CordaAvroSerializer<Any>): ProducerChunkService {
        val maxAllowedMessageSize = config.getLong(MessagingConfig.MAX_ALLOWED_MSG_SIZE)
        return ProducerChunkServiceImpl(maxAllowedMessageSize, cordaAvroSerializer, chunkBuilderService)
    }
}
