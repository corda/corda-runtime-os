package net.corda.introspiciere.http

import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.jackson.objectBody
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.kittinunf.result.Result
import net.corda.introspiciere.domain.IntrospiciereException
import net.corda.introspiciere.domain.KafkaMessage
import net.corda.introspiciere.domain.TopicDefinitionPayload
import net.corda.introspiciere.payloads.KafkaMessagesBatch

class IntrospiciereHttpClient(private val endpoint: String) {

    /**
     * Dummy request to test connection with the server. Might disappear in the future.
     */
    fun greetings(): String {
        val (_, response, result) = "$endpoint/helloworld".httpGet().timeoutRead(180000).responseString()

        return when (result) {
            is Result.Success -> result.get()
            is Result.Failure -> throw IntrospiciereException(result.getException().message, response.buildException())
        }
    }

    /**
     * Request to create a single topic to server.
     */
    fun createTopic(name: String, partitions: Int?, replicationFactor: Short?, config: Map<String, String>) {
        val topic = TopicDefinitionPayload(partitions, replicationFactor, config)
        val (_, response, result) = "$endpoint/topics/$name".httpPost()
            .objectBody(topic)
            .timeoutRead(180000)
            .responseString()

        when (result) {
            is Result.Success -> result.get()
            is Result.Failure -> throw IntrospiciereException(result.getException().message, response.buildException())
        }
    }

    /**
     * Request beginning offsets of a topic. [beginningOffsets] or [endOffsets] should be called before [readMessages].
     */
    fun beginningOffsets(topic: String, schemaClass: String): LongArray =
        fetchOffsets(topic, schemaClass, "beginningOffsets")

    /**
     * Request end offsets of a topic. [beginningOffsets] or [endOffsets] should be called before [readMessages].
     */
    fun endOffsets(topic: String, schemaClass: String): LongArray =
        fetchOffsets(topic, schemaClass, "endOffsets")

    private fun fetchOffsets(topic: String, schemaClass: String, operation: String): LongArray {
        val (_, response, result) = "$endpoint/topics/$topic/$operation"
            .httpGet("schema" to schemaClass)
            .timeoutRead(180000)
            .responseObject<LongArray>()

        return when (result) {
            is Result.Success -> result.get()
            is Result.Failure -> throw IntrospiciereException(result.getException().message, response.buildException())
        }
    }

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
        val (_, response, result) = "$endpoint/topics/${kafkaMessage.topic}/messages/${kafkaMessage.key}".httpPost()
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