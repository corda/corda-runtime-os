package net.corda.introspiciere.http

import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.kittinunf.result.Result
import net.corda.introspiciere.domain.KafkaMessage


class MessageReaderReq<T>(private val topic: String, private val key: String, private val schema: Class<T>) {
    fun request(endpoint: String): List<KafkaMessage> {
        val (_, response, result) = "$endpoint/topics/$topic/$key"
            .httpGet(listOf("schema" to schema.canonicalName))
            .timeoutRead(180000)
            .responseObject<List<KafkaMessage>>()

        return when (result) {
            is Result.Success -> result.get()
            is Result.Failure -> throw IntrospiciereException(result.getException().message, response.buildException())
        }
    }
}

