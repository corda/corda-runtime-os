package net.corda.introspiciere.http

import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.jackson.objectBody
import com.github.kittinunf.result.Result
import net.corda.introspiciere.domain.TopicDefinition

class CreateTopicReq(private val topicDefinition: TopicDefinition) {
    fun request(endpoint: String) {
        val (_, response, result) = "$endpoint/topics".httpPost()
            .objectBody(topicDefinition)
            .timeoutRead(180000)
            .responseString()
        when (result) {
            is Result.Success -> result.get()
            is Result.Failure -> throw IntrospiciereException(result.getException().message, response.buildException())
        }
    }
}
