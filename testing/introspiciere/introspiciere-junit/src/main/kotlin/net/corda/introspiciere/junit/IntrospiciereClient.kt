package net.corda.introspiciere.junit

import net.corda.introspiciere.domain.KafkaMessage
import net.corda.introspiciere.domain.TopicDefinition
import net.corda.introspiciere.domain.TopicDefinition.Companion.DEFAULT_PARTITIONS
import net.corda.introspiciere.domain.TopicDefinition.Companion.DEFAULT_REPLICATION_FACTOR
import net.corda.introspiciere.http.CreateTopicReq
import net.corda.introspiciere.http.MessageReaderReq
import net.corda.introspiciere.http.MessageWriterReq
import java.nio.ByteBuffer

/**
 * Client to interact with the instrospiciere server.
 */
class IntrospiciereClient(private val endpoint: String) {

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
        partitions: Int = DEFAULT_PARTITIONS,
        replicationFactor: Short = DEFAULT_REPLICATION_FACTOR,
    ) {
        val topic = TopicDefinition(name, partitions, replicationFactor)
        CreateTopicReq(topic).request(endpoint)
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
        MessageWriterReq(
            KafkaMessage(
                topic = topic,
                key = key,
                schema = schema,
                schemaClass = schemaClass
            )
        ).request(endpoint)
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
        val messages = MessageReaderReq(topic, key, schemaClass).request(endpoint)
        return messages.map {
            val method = schemaClass.getMethod("fromByteBuffer", ByteBuffer::class.java)
            @Suppress("UNCHECKED_CAST")
            method.invoke(null, ByteBuffer.wrap(it.schema)) as T
        }
    }
}
