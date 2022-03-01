package net.corda.introspiciere.server

import io.javalin.http.Context
import io.javalin.http.Handler
import net.corda.introspiciere.domain.IntrospiciereException
import net.corda.introspiciere.domain.KafkaMessage
import net.corda.introspiciere.payloads.MsgBatch

internal class MessagesController(private val appContext: AppContext) {

    fun writeMessage() = Handler { ctx ->
        val kafkaMessage = ctx.bodyAsClass<KafkaMessage>()
        appContext.messagesGateway.send(ctx.topicParam, kafkaMessage)
    }

    fun getMessages() = Handler { ctx ->
        val (messages, timestamp) = appContext.messagesGateway.readFrom(ctx.topicParam, ctx.schemaQuery, ctx.fromQuery)
        val nextBatchTimestamp = if (messages.isEmpty()) timestamp else messages.maxOf { it.timestamp } + 1
        val batch = MsgBatch(ctx.schemaQuery, messages, nextBatchTimestamp)
        ctx.json(batch)
    }

    private val Context.topicParam: String
        get() = pathParam("topic")

    private val Context.fromQuery: Long
        get() = queryParam("from")?.toLong() ?: throw IntrospiciereException("from is mandatory field in query")

    private val Context.keyQuery: String?
        get() = queryParam("key")

    private val Context.schemaQuery: String
        get() = queryParam("schema") ?: throw IntrospiciereException("schema is mandatory field in query")
}