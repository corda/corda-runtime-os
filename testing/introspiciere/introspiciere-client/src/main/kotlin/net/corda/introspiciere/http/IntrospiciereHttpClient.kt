package net.corda.introspiciere.http

import com.github.kittinunf.fuel.core.ResponseResultOf
import com.github.kittinunf.fuel.httpDelete
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.jackson.objectBody
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.kittinunf.result.Result
import net.corda.introspiciere.domain.KafkaMessage
import net.corda.introspiciere.domain.TopicDefinitionPayload
import net.corda.introspiciere.domain.TopicDescription
import net.corda.introspiciere.payloads.MsgBatch
import java.time.Duration

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
        "$endpoint/topics".httpPost()
            .objectBody(topic)
            .timeoutRead(180000)
            .responseString()
            .getOrThrow()
    }

    fun listTopics(): Set<String> = get("$endpoint/topics")

    fun describeTopic(name: String): TopicDescription = get("$endpoint/topics/$name")

    fun deleteTopic(name: String) = delete("$endpoint/topics/$name")

    fun readFromBeginning(topic: String, key: String?, schema: String, timeout: Duration): MsgBatch =
        readInternal(topic, key, schema, 0, timeout)

    fun readFromEnd(topic: String, key: String?, schema: String, timeout: Duration): MsgBatch =
        readInternal(topic, key, schema, -1, timeout)

    fun readFrom(topic: String, key: String?, schema: String, from: Long, timeout: Duration): MsgBatch =
        readInternal(topic, key, schema, from, timeout)

    private fun readInternal(topic: String, key: String?, schema: String, from: Long, timeout: Duration): MsgBatch =
        get(
            "$endpoint/topics/$topic/messages",
            "key" to key,
            "schema" to schema,
            "from" to from,
            "timeout" to timeout.toMillis()
        )

    /**
     * Request to send a message to Kafka.
     */
    fun sendMessage(kafkaMessage: KafkaMessage) {
        "$endpoint/topics/${kafkaMessage.topic}/messages".httpPost()
            .objectBody(kafkaMessage)
            .timeoutRead(180000)
            .responseString()
            .getOrThrow()
    }

    private inline fun <reified T : Any> get(path: String, vararg params: Pair<String, Any?>): T {
        val listOrNull = if (params.isEmpty()) null else params.toList()
        return path.httpGet(listOrNull).timeoutRead(180000).responseObject<T>().getOrThrow()
    }

    private fun delete(path: String, vararg params: Pair<String, Any?>) {
        val listOrNull = if (params.isEmpty()) null else params.toList()
        path.httpDelete(listOrNull).timeoutRead(180000).responseString().getOrThrow()
    }

    private fun <T> ResponseResultOf<T>.getOrThrow(): T {
        val (request, response, result) = this
        println(request)
        println(response)
        println(result)
        return when (result) {
            is Result.Success -> result.get()
            is Result.Failure -> throw IntrospiciereHttpClientException(request, response, result)
        }
    }
}