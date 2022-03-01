package net.corda.introspiciere.junit

import net.corda.introspiciere.domain.KafkaMessage
import net.corda.introspiciere.domain.TopicDescription
import net.corda.introspiciere.http.IntrospiciereHttpClient
import net.corda.introspiciere.payloads.MsgBatch
import net.corda.introspiciere.payloads.deserialize

/**
 * Client to interact with the instrospiciere server.
 */
@Suppress("UNUSED_PARAMETER")
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

    fun listTopics(): Set<String> = httpClient.listTopics()

    fun describeTopic(name: String): TopicDescription = httpClient.describeTopic(name)

    fun deleteTopic(name: String): Unit = httpClient.deleteTopic(name)

    /**
     * Write a message in a Kafka [topic] using the key/value format. You can read more about keys and partitions
     * [in the official docs](https://www.confluent.io/blog/5-things-every-kafka-developer-should-know/#tip-2-new-sticky-partitioner).
     *
     * [schema] must be of Avro generated class.
     */
    fun write(topic: String, key: String?, schema: Any) {
        writeInternal(KafkaMessage.create(topic, key, schema))
    }

    /**
     * Write a message in a Kafka [topic] using the key/value format. You can read more about keys and partitions
     * [in the official docs](https://www.confluent.io/blog/5-things-every-kafka-developer-should-know/#tip-2-new-sticky-partitioner).
     *
     * [schema] is the byte array of an an instance serialised. [schemaClass] is the qualified name of the Avro schema class.
     */
    fun write(topic: String, key: String?, schema: ByteArray, schemaClass: String) {
        writeInternal(KafkaMessage(topic, key, schema, schemaClass))
    }

    fun writeInternal(message: KafkaMessage) {
        httpClient.sendMessage(message)
    }

    /**
     * Returns a sequence with all messages from a [topic] with a [key]. Starts reading messages from the beginning.
     */
    inline fun <reified T> readFromBeginning(topic: String, key: String?): Sequence<T?> =
        readFromBeginning(topic, key, T::class.java)

    /**
     * Returns a sequence with all messages from a [topic] with a [key]. Starts reading messages from the end.
     */
    inline fun <reified T> readFromLatest(topic: String, key: String?): Sequence<T?> =
        readFromLatest(topic, key, T::class.java)

    /**
     * Returns a sequence with all messages from a [topic] with a [key]. Starts reading messages from the beginning.
     */
    fun <T> readFromBeginning(topic: String, key: String?, schemaClass: Class<T>): Sequence<T?> =
        readInternal(topic, key, schemaClass, httpClient::readFromBeginning)

    /**
     * Returns a sequence with all messages from a [topic] with a [key]. Starts reading messages from the end.
     */
    fun <T> readFromLatest(topic: String, key: String?, schemaClass: Class<T>): Sequence<T?> =
        readInternal(topic, key, schemaClass, httpClient::readFromEnd)

    private fun <T> readInternal(
        topic: String, key: String?, schemaClass: Class<T>, readFromMethod: (String, String?, String) -> MsgBatch,
    ): Sequence<T?> {
        val qualifiedName = schemaClass::class.qualifiedName!!
        var batch = readFromMethod(topic, key, qualifiedName)

        return sequence {
            batch.messages.forEach { msg -> yield(msg.deserialize(schemaClass)) }
            while (true) {
                batch = httpClient.readFrom(topic, key, qualifiedName, batch.nextBatchTimestamp)
                batch.messages.forEach { msg -> yield(msg.deserialize(schemaClass)) }
                if (batch.messages.isEmpty()) yield(null)
            }
        }
    }
}
