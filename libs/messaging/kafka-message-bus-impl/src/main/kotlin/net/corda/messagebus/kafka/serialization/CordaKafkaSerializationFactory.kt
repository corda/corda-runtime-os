package net.corda.messagebus.kafka.serialization

interface CordaKafkaSerializationFactory {
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