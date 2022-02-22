package net.corda.introspiciere.http

import com.github.kittinunf.fuel.httpPut
import com.github.kittinunf.fuel.jackson.objectBody
import com.github.kittinunf.result.Result
import net.corda.introspiciere.domain.KafkaMessage

class MessageWriterReq(private val kafkaMessage: KafkaMessage) {
    fun request(endpoint: String) {
        val (_, response, result) = "$endpoint/topics".httpPut()
            .objectBody(kafkaMessage)
            .timeoutRead(180000)
            .responseString()
        when (result) {
            is Result.Success -> result.get()
            is Result.Failure -> throw IntrospiciereException(result.getException().message, response.buildException())
        }
    }
}
