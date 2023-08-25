package net.corda.avro.serialization


/**
 * Defines the interface for message bus deserialization. The underlying mechanism may differ.
 */
interface CordaAvroSerializationFactory {

    /**
     * Create the {@link CordaAvroSerializer} for use in Avro/message bus serialization.
     *
     * @param T the type to serialize
     * @param onError a lambda to run on serialization error, will run regardless of throwOnSerializationError
     * @return an implementation of CordaAvroSerializer
     */
    fun <T : Any> createAvroSerializer(
        onError: ((ByteArray) -> Unit)? = null
    ): CordaAvroSerializer<T>

    /**
     * Create an Adaptor that can be used in place of Kafka Serializer<>
     *
     * @param onError a lambda to run on serialization error
     * @return an Adaptor for Kafka Serializer<>
     */
    fun createAvroBasedKafkaSerializer(
        onError: ((ByteArray) -> Unit)? = null
    ): (Any?, Any?) -> ByteArray?

    /**
     * Create the {@link CordaAvroDeserializer} for use in Avro/message bus serialization.
     *
     * @param T
     * @param onError lambda to be run on deserialization error
     * @param expectedClass the expected class type to serialize
     * @return an implementation of the CordaAvroDeserializer
     */
    fun <T : Any> createAvroDeserializer(
        onError: (ByteArray) -> Unit,
        expectedClass: Class<T>
    ): CordaAvroDeserializer<T>

    /**
     * Create an Adaptor that can be used in place of Kafka Deserializer<>
     *
     * @param onError lambda to be run on deserialization error
     * @param expectedClass the expected class type to serialize
     * @return an Adaptor for Kafka Deserializer<>
     */
    fun <T : Any> createAvroBasedKafkaDeserializer(
        onError: (ByteArray) -> Unit,
        expectedClass: Class<T>
    ): (Any?, ByteArray?) -> T?
}