package net.corda.introspiciere.http

import com.github.kittinunf.fuel.gson.jsonBody
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPut
import com.github.kittinunf.result.Result
import net.corda.introspiciere.domain.KafkaMessage

class MessageWriterReq(private val kafkaMessage: KafkaMessage) {
    fun request(endpoint: String) {
        val (_, response, result) = "$endpoint/topics".httpPut()
            .jsonBody(kafkaMessage)
            .timeoutRead(180000)
            .responseString()
        when (result) {
            is Result.Success -> result.get()
            is Result.Failure -> throw IntrospiciereException(result.getException().message, response.buildException())
        }
    }
}

class MessageReaderReq(private val topic: String, private val key: String, private val schemaClass: String) {
    fun request(endpoint: String) {
        val (_, response, result) = "$endpoint/topics/$topic/$key"
            .httpGet(listOf("schema" to schemaClass))
            .timeoutRead(180000)
            .responseString()
        when (result) {
            is Result.Success -> result.get()
            is Result.Failure -> throw IntrospiciereException(result.getException().message, response.buildException())
        }
    }
}