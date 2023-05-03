package net.corda.messaging.chunking

import net.corda.chunking.ChunkBuilderService
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.messaging.api.chunking.ChunkDeserializerService
import net.corda.messaging.api.chunking.ChunkSerializerService
import net.corda.messaging.api.chunking.ConsumerChunkDeserializerService
import net.corda.messaging.api.chunking.MessagingChunkFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [MessagingChunkFactory::class])
class MessagingChunkFactoryImpl @Activate constructor(
    @Reference(service = ChunkBuilderService::class)
    private val chunkBuilderService: ChunkBuilderService,
    @Reference(service = CordaAvroSerializationFactory::class)
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    @Reference(service = PlatformDigestService::class)
    private val platformDigestService: PlatformDigestService,
) : MessagingChunkFactory {

    override fun <K : Any, V : Any> createConsumerChunkDeserializerService(
        keyDeserializer: CordaAvroDeserializer<K>,
        valueDeserializer: CordaAvroDeserializer<V>,
        onError: (ByteArray) -> Unit,
    ): ConsumerChunkDeserializerService<K, V> {
        return ChunkDeserializerServiceImpl(keyDeserializer, valueDeserializer, onError, platformDigestService)
    }

    override fun <V : Any> createChunkDeserializerService(
        expectedType: Class<V>,
        onError: (ByteArray) -> Unit,
    ): ChunkDeserializerService<V> {
        val deserializer = cordaAvroSerializationFactory.createAvroDeserializer(onError, expectedType)
        return ChunkDeserializerServiceImpl(deserializer, deserializer, onError, platformDigestService)
    }

    override fun createChunkSerializerService(maxAllowedMessageSize: Long): ChunkSerializerService {
        return ChunkSerializerServiceImpl(maxAllowedMessageSize,
            cordaAvroSerializationFactory.createAvroSerializer {},
            chunkBuilderService, platformDigestService)
    }
}
