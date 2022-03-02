package net.corda.introspiciere.server

import io.javalin.http.Context
import io.javalin.http.Handler
import net.corda.introspiciere.domain.TopicDefinition
import net.corda.introspiciere.domain.TopicDefinitionPayload

internal class TopicController(private val appContext: AppContext) {
    fun create(): Handler = Handler { ctx ->
        val kafkaMessage = ctx.bodyAsClass(TopicDefinitionPayload::class.java)
        appContext.topicGateway.create(TopicDefinition(
            kafkaMessage.name,
            kafkaMessage.partitions,
            kafkaMessage.replicationFactor,
            kafkaMessage.config
        ))
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
        wrapException {
            appContext.topicGateway.removeByName(ctx.topicParam)
        }
    }

    private val Context.topicParam: String
        get() = pathParam("topic")
}