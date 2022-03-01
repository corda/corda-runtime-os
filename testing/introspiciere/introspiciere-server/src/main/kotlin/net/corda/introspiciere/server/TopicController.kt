package net.corda.introspiciere.server

import io.javalin.http.Handler
import net.corda.introspiciere.core.CreateTopicUseCase
import net.corda.introspiciere.core.KafkaAdminGatewayImpl
import net.corda.introspiciere.core.KafkaConfig
import net.corda.introspiciere.core.ReadMessagesUseCases
import net.corda.introspiciere.domain.TopicDefinition
import net.corda.introspiciere.domain.TopicDefinitionPayload

internal class TopicController(private val context: Context) {
    fun create(): Handler = Handler { ctx ->
        wrapException {
            val topic = ctx.pathParam("topic")
            val kafkaMessage = ctx.bodyAsClass<TopicDefinitionPayload>()
            CreateTopicUseCase(context.topicGateway).execute(TopicDefinition(
                topic,
                kafkaMessage.partitions,
                kafkaMessage.replicationFactor,
                kafkaMessage.config
            ))
        }
    }

    fun beginningOffsets(): Handler = Handler { ctx ->
        wrapException {
            val topic = ctx.pathParam("topic")
            val schema = ctx.queryParam("schema")!!
            val offsets = ReadMessagesUseCases(context.kafkaConfig).beginningOffsets(topic, schema)
            ctx.json(offsets)
        }
    }

    fun endOffsets(): Handler = Handler { ctx ->
        wrapException {
            val topic = ctx.pathParam("topic")
            val schema = ctx.queryParam("schema")!!
            val offsets = ReadMessagesUseCases(context.kafkaConfig).endOffsets(topic, schema)
            ctx.json(offsets)
        }
    }
}