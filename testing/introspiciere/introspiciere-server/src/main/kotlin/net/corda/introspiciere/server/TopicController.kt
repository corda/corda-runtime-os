package net.corda.introspiciere.server

import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.http.HttpCode.OK
import net.corda.introspiciere.core.CreateTopicUseCase
import net.corda.introspiciere.core.ReadMessagesUseCases
import net.corda.introspiciere.domain.TopicDefinition
import net.corda.introspiciere.domain.TopicDefinitionPayload

internal class TopicController(private val appContext: AppContext) {
    fun create(): Handler = Handler { ctx ->
        wrapException {
            val kafkaMessage = ctx.bodyAsClass<TopicDefinitionPayload>()
            CreateTopicUseCase(appContext.topicGateway).execute(TopicDefinition(
                kafkaMessage.name,
                kafkaMessage.partitions,
                kafkaMessage.replicationFactor,
                kafkaMessage.config
            ))
        }
    }

    fun beginningOffsets(): Handler = Handler { ctx ->
        wrapException {
            val schema = ctx.queryParam("schema")!!
            val offsets = ReadMessagesUseCases(appContext.kafkaConfig).beginningOffsets(ctx.topicParam, schema)
            ctx.json(offsets)
        }
    }

    fun endOffsets(): Handler = Handler { ctx ->
        wrapException {
            val schema = ctx.queryParam("schema")!!
            val offsets = ReadMessagesUseCases(appContext.kafkaConfig).endOffsets(ctx.topicParam, schema)
            ctx.json(offsets)
        }
    }

    fun getAll() = Handler { ctx ->
        val topics = appContext.topicGateway.findAll()
        ctx.json(topics)
    }

    fun getByName() = Handler { ctx ->
        val description = appContext.topicGateway.findByName(ctx.topicParam)
        ctx.json(description)
    }

    fun delete() = Handler { ctx ->
        appContext.topicGateway.removeByName(ctx.topicParam)
        ctx.status(OK)
    }

    private val Context.topicParam: String
        get() = pathParam("topic")
}