package net.corda.introspiciere.http

import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.jackson.objectBody
import com.github.kittinunf.result.Result
import net.corda.introspiciere.domain.TopicDefinitionPayload

class IntrospiciereHttpClient(private val endpoint: String) {

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
}