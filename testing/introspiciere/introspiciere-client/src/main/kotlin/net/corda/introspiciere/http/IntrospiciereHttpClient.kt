package net.corda.introspiciere.http

import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.httpDelete
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.jackson.objectBody
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.kittinunf.result.Result
import net.corda.introspiciere.domain.IntrospiciereException
import net.corda.introspiciere.domain.KafkaMessage
import net.corda.introspiciere.domain.TopicDefinitionPayload
import net.corda.introspiciere.domain.TopicDescription
import net.corda.introspiciere.payloads.KafkaMessagesBatch
import net.corda.introspiciere.payloads.MsgBatch

class IntrospiciereHttpClient(private val endpoint: String) {

    /**
     * Dummy request to test connection with the server. Might disappear in the future.
     */
    fun greetings(): String = get("$endpoint/helloworld")

    /**
     * Request to create a single topic to server.
     */
    fun createTopic(name: String, partitions: Int?, replicationFactor: Short?, config: Map<String, String>) {
        val topic = TopicDefinitionPayload(name, partitions, replicationFactor, config)
        val (_, response, result) = "$endpoint/topics".httpPost()
            .objectBody(topic)
            .timeoutRead(180000)
            .responseString()

        when (result) {
            is Result.Success -> result.get()
            is Result.Failure -> throw IntrospiciereException(result.getException().message, response.buildException())
        }
    }

    fun listTopics(): Set<String> = get("$endpoint/topics")

    fun describeTopic(name: String): TopicDescription = get("$endpoint/topics/$name")

    fun deleteTopic(name: String) = delete("$endpoint/topics/$name")

    private inline fun <reified T : Any> get(path: String, vararg params: Pair<String, Any?>): T {
        val listOrNull = if (params.isEmpty()) null else params.toList()
        val (_, response, result) = path.httpGet(listOrNull)
            .timeoutRead(180000)
            .responseObject<T>()

        return when (result) {
            is Result.Success -> result.get()
            is Result.Failure -> throw IntrospiciereException(result.getException().message, response.buildException())
        }
    }

    private fun delete(path: String, vararg params: Pair<String, Any?>) {
        val listOrNull = if (params.isEmpty()) null else params.toList()
        val (_, response, result) = path.httpDelete(listOrNull)
            .timeoutRead(180000)
            .responseString()

        when (result) {
            is Result.Success -> result.get()
            is Result.Failure -> throw IntrospiciereException(result.getException().message, response.buildException())
        }
    }

    fun readFromBeginning(topic: String, key: String?, schema: String): MsgBatch =
        readInternal(topic, key, schema, 0)

    fun readFromEnd(topic: String, key: String?, schema: String): MsgBatch =
        readInternal(topic, key, schema, -1)

    fun readFrom(topic: String, key: String?, schema: String, from: Long): MsgBatch =
        readInternal(topic, key, schema, from)

    private fun readInternal(topic: String, key: String?, schema: String, from: Long): MsgBatch =
        get("$endpoint/topics/$topic/messages", "key" to key, "schema" to schema, "from" to from)

    /**
     * Fetch messages from a topic for a [key]. [beginningOffsets] or [endOffsets] should be called before [readMessages].
     */
    fun readMessages(topic: String, key: String, schema: String, from: LongArray): KafkaMessagesBatch {
        val (_, response, result) = "$endpoint/topics/$topic/messages/$key"
            .httpGet(
                "schema" to schema,
                "from" to from.joinToString(","),
            ).timeoutRead(180000)
            .responseObject<KafkaMessagesBatch>()

        return when (result) {
            is Result.Success -> result.get()
            is Result.Failure -> throw IntrospiciereException(result.getException().message, response.buildException())
        }
    }

    /**
     * Request to send a message to Kafka.
     */
    fun sendMessage(kafkaMessage: KafkaMessage) {
        val (_, response, result) = "$endpoint/topics/${kafkaMessage.topic}/messages".httpPost()
            .objectBody(kafkaMessage)
            .timeoutRead(180000)
            .responseString()

        when (result) {
            is Result.Success -> result.get()
            is Result.Failure -> throw IntrospiciereException(result.getException().message, response.buildException())
        }
    }
}

private fun String.httpGet(vararg parameters: Pair<String, Any?>): Request =
    httpGet(parameters.toList())