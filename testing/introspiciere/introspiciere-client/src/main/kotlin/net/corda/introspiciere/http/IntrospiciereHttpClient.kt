package net.corda.introspiciere.http

import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.jackson.objectBody
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.kittinunf.result.Result
import net.corda.introspiciere.domain.KafkaMessage
import net.corda.introspiciere.domain.TopicDefinitionPayload

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
     * Request messages from a kafka topic/key.
     */
    fun readMessages(topic: String, key: String, schema: String): List<KafkaMessage> {
        val (_, response, result) = "$endpoint/topics/$topic/messages/$key"
            .httpGet(listOf("schema" to schema))
            .timeoutRead(180000)
            .responseObject<List<KafkaMessage>>()

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