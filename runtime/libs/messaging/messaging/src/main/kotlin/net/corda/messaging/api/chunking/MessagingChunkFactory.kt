package net.corda.messaging.api.chunking

import net.corda.data.CordaAvroDeserializer

/**
 * Factory class for instantiating services used by consumer and producers to produce and reassemble chunks.
 */
interface MessagingChunkFactory {

    /**
     * Create a service which can reassemble chunks into their original values or key/value pairs.
     * @param keyDeserializer used to deserialize the objects original key if it has one
     * @param valueDeserializer used to deserialize the objects original value
     * @param onError error handling to execute if it fails to reassemble the chunks. takes the fully reassembled byte array
     * @return ConsumerChunkDeserializerService
     */
    fun <K: Any, V: Any> createConsumerChunkDeserializerService(
        keyDeserializer: CordaAvroDeserializer<K>,
        valueDeserializer: CordaAvroDeserializer<V>,
        onError: (ByteArray) -> Unit,
    ): ConsumerChunkDeserializerService<K, V>

    /**
     * Create a service which can reassemble chunks into their original values .
     * @param expectedType The expected type of the class the chunks will be deserialized into
     * @param onError error handling to execute if it fails to reassemble the chunks. takes the fully reassembled byte array
     * @return ChunkDeserializerService
     */
    fun <V: Any> createChunkDeserializerService(
        expectedType: Class<V>,
        onError: (ByteArray) -> Unit = {_ ->}
    ): ChunkDeserializerService<V>

    /**
     * Service to convert objects or CordaProducerRecords into chunks if they are too large to be sent
     * @param maxAllowedMessageSize the max allowed message size for records on the message bus
     * @return ProducerChunkService
     */
    fun createChunkSerializerService(maxAllowedMessageSize: Long): ChunkSerializerService
}
