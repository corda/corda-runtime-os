package net.corda.introspiciere.http

import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.kittinunf.result.Result
import net.corda.introspiciere.domain.KafkaMessage


class MessageReaderReq(private val topic: String, private val key: String, private val schema: String) {

    constructor(topic: String, key: String, schema: Class<*>): this(topic, key, schema.canonicalName)

    fun request(endpoint: String): List<KafkaMessage> {
        val (_, response, result) = "$endpoint/topics/$topic/$key"
            .httpGet(listOf("schema" to schema))
            .timeoutRead(180000)
            .responseObject<List<KafkaMessage>>()

        return when (result) {
            is Result.Success -> result.get()
            is Result.Failure -> throw IntrospiciereException(result.getException().message, response.buildException())
        }
    }
}

