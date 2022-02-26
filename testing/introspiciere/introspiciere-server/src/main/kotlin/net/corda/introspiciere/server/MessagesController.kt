package net.corda.introspiciere.server

import io.javalin.http.Handler
import net.corda.introspiciere.core.KafkaConfig
import net.corda.introspiciere.core.ReadMessagesUseCases
import net.corda.introspiciere.core.WriteMessageUseCase
import net.corda.introspiciere.domain.KafkaMessage
import net.corda.introspiciere.payloads.KafkaMessagesBatch

internal class MessagesController(private val kafkaConfig: KafkaConfig) {
    fun getAll(): Handler = Handler { ctx ->
        wrapException {
            val topic = ctx.pathParam("topic")
            val key = ctx.pathParam("key")
            val schema = ctx.queryParam("schema")!!
            val from = ctx.queryParam("from")!!.split(",").map(String::toLong)

            var batch = KafkaMessagesBatch(schema, emptyList(), LongArray(0))
            ReadMessagesUseCases(kafkaConfig).readFrom(topic, key, schema, from, object : ReadMessagesUseCases.Output {
                override fun messages(byteArrays: List<ByteArray>) {
                    batch = batch.copy(messages = byteArrays)
                }

                override fun latestOffsets(offsets: LongArray) {
                    batch = batch.copy(latestOffsets = offsets)
                }
            })

            ctx.json(batch)
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