package net.corda.introspiciere.junit

import net.corda.introspiciere.domain.KafkaMessage
import net.corda.introspiciere.http.IntrospiciereHttpClient
import java.nio.ByteBuffer

/**
 * Client to interact with the instrospiciere server.
 */
class IntrospiciereClient(private val endpoint: String) {

    private val httpClient = IntrospiciereHttpClient(endpoint)

    /**
     * Dummy method to initially test the client-server connexion. It will disappear eventually.
     */
    fun helloWorld() {
        println("I should call $endpoint/helloworld")
    }

    /**
     * Creates a topic in Kafka. Default values for [partitions] and [replicationFactor] is 1.
     */
    fun createTopic(
        name: String,
        partitions: Int? = null,
        replicationFactor: Short? = null,
        config: Map<String, Any>? = null,
    ) {
        val mapOfStrings = (config ?: emptyMap()).map { it.key to it.value.toString() }.toMap()
        httpClient.createTopic(name, partitions, replicationFactor, mapOfStrings)
    }

    /**
     * Write a message in a Kafka [topic] using the key/value format. You can read more about keys and partitions
     * [in the official docs](https://www.confluent.io/blog/5-things-every-kafka-developer-should-know/#tip-2-new-sticky-partitioner).
     *
     * [schema] must be of Avro generated class.
     */
    fun write(topic: String, key: String, schema: Any) {
        val byteBuffer = schema::class.java.getMethod("toByteBuffer").invoke(schema) as ByteBuffer
        write(topic, key, byteBuffer.toByteArray(), schema::class.qualifiedName!!)
    }

    /**
     * Write a message in a Kafka [topic] using the key/value format. You can read more about keys and partitions
     * [in the official docs](https://www.confluent.io/blog/5-things-every-kafka-developer-should-know/#tip-2-new-sticky-partitioner).
     *
     * [schema] is the byte array of an an instance serialised. [schemaClass] is the qualified name of the Avro schema class.
     */
    fun write(topic: String, key: String, schema: ByteArray, schemaClass: String) {
        httpClient.sendMessage(KafkaMessage(
            topic = topic,
            key = key,
            schema = schema,
            schemaClass = schemaClass
        ))
    }

    /**
     * Read all messages from a topic under a given key. More about keys
     * [in the official docs](https://www.confluent.io/blog/5-things-every-kafka-developer-should-know/#tip-2-new-sticky-partitioner).
     */
    inline fun <reified T> read(topic: String, key: String): List<T> {
        return read(topic, key, T::class.java)
    }

    /**
     * Read all messages from a topic under a given key. More about keys
     * [in the official docs](https://www.confluent.io/blog/5-things-every-kafka-developer-should-know/#tip-2-new-sticky-partitioner).
     */
    fun <T> read(topic: String, key: String, schemaClass: Class<T>): List<T> {
        val messages = httpClient.readMessages(topic, key, schemaClass.canonicalName)
        return messages.map {
            val method = schemaClass.getMethod("fromByteBuffer", ByteBuffer::class.java)
            @Suppress("UNCHECKED_CAST")
            method.invoke(null, ByteBuffer.wrap(it.schema)) as T
        }
    }
}
