package net.corda.messaging.api.chunking

import java.util.function.Consumer
import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializer
import net.corda.libs.configuration.SmartConfig

/**
 * Factory class for instantiating services used by consumer and producers to produce and reassemble chunks.
 */
interface MessagingChunkFactory {

    /**
     * Create a service which can reassemble chunks into their original values or key/value pairs.
     * @param keyDeserializer used to deserialize the objects original key if it has one
     * @param valueDeserializer used to deserialize the objects original value
     * @param onError error handling to execute if it fails to reassemble the chunks. takes the fully reassembled byte array
     * @return ConsumerChunkService
     */
    fun <K: Any, V: Any> createConsumerChunkService(
        keyDeserializer: CordaAvroDeserializer<K>,
        valueDeserializer: CordaAvroDeserializer<V>,
        onError: Consumer<ByteArray>
    ): ConsumerChunkService<K, V>

    /**
     * Service to convert objects or CordaProducerRecords into chunks if they are too large to be sent
     * @param config contains which the max allowed message size
     * @param cordaAvroSerializer serializer for converting avro objects to byte arrays
     * @return ProducerChunkService
     */
    fun createProducerChunkService(config: SmartConfig, cordaAvroSerializer: CordaAvroSerializer<Any>): ProducerChunkService
}
