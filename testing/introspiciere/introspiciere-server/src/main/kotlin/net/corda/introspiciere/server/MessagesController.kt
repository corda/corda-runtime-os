package net.corda.introspiciere.server

import io.javalin.http.Handler
import net.corda.introspiciere.core.KafkaConfig
import net.corda.introspiciere.core.KafkaMessageGateway
import net.corda.introspiciere.core.KafkaReaderGateway
import net.corda.introspiciere.domain.KafkaMessage

internal class MessagesController(private val kafkaConfig: KafkaConfig) {
    fun getAll(): Handler = Handler { ctx ->
        wrapException {
            val topic = ctx.pathParam("topic")
            val key = ctx.pathParam("key")
            val schema = ctx.queryParam("schema")!!
            val messages = KafkaReaderGateway(listOf(kafkaConfig.brokers)).read(topic, key, schema)
            ctx.json(messages)
        }
    }

    fun create(): Handler = Handler { ctx ->
        wrapException {
            val kafkaMessage = ctx.bodyAsClass<KafkaMessage>()
            KafkaMessageGateway(listOf(kafkaConfig.brokers)).send(kafkaMessage)
            ctx.result("OK")
        }
    }
}