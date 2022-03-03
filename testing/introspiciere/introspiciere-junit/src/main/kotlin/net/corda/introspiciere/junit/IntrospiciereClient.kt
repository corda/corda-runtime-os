package net.corda.introspiciere.junit

import net.corda.introspiciere.domain.KafkaMessage
import net.corda.introspiciere.domain.TopicDescription
import net.corda.introspiciere.http.IntrospiciereHttpClient
import net.corda.introspiciere.payloads.MsgBatch
import net.corda.introspiciere.payloads.deserialize
import java.time.Duration
import kotlin.concurrent.thread

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
    fun <T> readFromBeginning(
        topic: String,
        key: String?,
        schemaClass: Class<T>,
        timeout: Duration = Duration.ofSeconds(1),
    ): Sequence<T?> = readInternal(topic, key, schemaClass, timeout, httpClient::readFromBeginning)

    /**
     * Returns a sequence with all messages from a [topic] with a [key]. Starts reading messages from the end.
     */
    fun <T> readFromLatest(
        topic: String,
        key: String?,
        schemaClass: Class<T>,
        timeout: Duration = Duration.ofSeconds(1),
    ): Sequence<T?> = readInternal(topic, key, schemaClass, timeout, httpClient::readFromEnd)

    private fun <T> readInternal(
        topic: String,
        key: String?,
        schemaClass: Class<T>,
        timeout: Duration,
        readFromMethod: (String, String?, String, Duration) -> MsgBatch,
    ): Sequence<T?> {
        val qualifiedName = schemaClass.canonicalName
        var batch = readFromMethod(topic, key, qualifiedName, timeout)

        return sequence {
            batch.messages.forEach { msg -> yield(msg.deserialize(schemaClass)) }
            while (true) {
                batch = httpClient.readFrom(topic, key, qualifiedName, batch.nextBatchTimestamp, timeout)
                if (batch.messages.isEmpty()) yield(null)
                else batch.messages.forEach { msg -> yield(msg.deserialize(schemaClass)) }
            }
        }
    }

    private val threads = mutableListOf<Thread>()
    private val exceptions = mutableListOf<Throwable>()
    private var continueThread = true

    fun <T> handle(topic: String, key: String?, schemaClass: Class<T>, action: (IntrospiciereClient, T) -> Unit) {
        threads += thread {
            val iter = readFromLatest(topic, key, schemaClass).iterator()
            while (continueThread) {
                val message = iter.next()

                if (message == null) {
                    Thread.sleep(2000)
                    continue
                }

                action(this, message)
            }
        }.apply {
            this.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, e -> exceptions += e }
        }
    }

    fun endAndjoinThreads() {
        continueThread = false
        threads.forEach(Thread::join)
        if (exceptions.isNotEmpty())
            throw ExceptionsFound(exceptions.first())
    }

    class ExceptionsFound(throwable: Throwable) : Exception("Exceptions found in any of the threads", throwable)
}

/**
 * Returns a sequence with all messages from a [topic] with a [key]. Starts reading messages from the beginning.
 */
inline fun <reified T> IntrospiciereClient.readFromBeginning(topic: String, key: String?): Sequence<T?> =
    readFromBeginning(topic, key, T::class.java)

/**
 * Returns a sequence with all messages from a [topic] with a [key]. Starts reading messages from the end.
 */
inline fun <reified T> IntrospiciereClient.readFromLatest(topic: String, key: String?): Sequence<T?> =
    readFromLatest(topic, key, T::class.java)

inline fun <reified T> IntrospiciereClient.handle(
    topic: String,
    key: String? = null,
    noinline action: (IntrospiciereClient, T) -> Unit,
) {
    return handle(topic, key, T::class.java, action)
}