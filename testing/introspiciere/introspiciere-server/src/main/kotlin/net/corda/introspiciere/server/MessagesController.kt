package net.corda.introspiciere.server

import io.javalin.http.Handler
import net.corda.introspiciere.core.KafkaConfig
import net.corda.introspiciere.core.ReadMessagesUseCases
import net.corda.introspiciere.core.WriteMessageUseCase
import net.corda.introspiciere.domain.KafkaMessage

internal class MessagesController(private val kafkaConfig: KafkaConfig) {
    fun getAll(): Handler = Handler { ctx ->
        wrapException {
            val topic = ctx.pathParam("topic")
            val key = ctx.pathParam("key")
            val schema = ctx.queryParam("schema")!!

            val useCase = ReadMessagesUseCases(kafkaConfig) {
                ctx.json(it)
            }

            useCase.execute(
                ReadMessagesUseCases.Input(topic, key, schema)
            )
        }
    }

    fun create(): Handler = Handler { ctx ->
        wrapException {
            val kafkaMessage = ctx.bodyAsClass<KafkaMessage>()

            WriteMessageUseCase(kafkaConfig).execute(WriteMessageUseCase.Input(
                kafkaMessage.topic,
                kafkaMessage.key,
                kafkaMessage.schema,
                kafkaMessage.schemaClass
            ))

            ctx.result("OK")
        }
    }
}