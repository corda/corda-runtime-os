package net.corda.introspiciere.server

import io.javalin.http.Context
import io.javalin.http.Handler
import net.corda.introspiciere.domain.IntrospiciereException
import net.corda.introspiciere.domain.KafkaMessage
import net.corda.introspiciere.payloads.MsgBatch
import java.time.Duration
import java.time.Instant

internal class MessagesController(private val appContext: AppContext) {

    fun writeMessage() = Handler { ctx ->
        val kafkaMessage = ctx.bodyAsClass(KafkaMessage::class.java)
        appContext.messagesGateway.send(ctx.topicParam, kafkaMessage)
    }

    fun getMessages() = Handler { ctx ->
        val (messages, nextBatchTimestamp) = appContext.messagesGateway.readFrom(
            ctx.topicParam,
            ctx.schemaQuery,
            ctx.fromQuery ?: Instant.now().toEpochMilli(),
            ctx.timeoutQuery ?: Duration.ofSeconds(1000)
        )
        
        val filter = if (ctx.keyQuery == null) messages
        else messages.filter { it.key == ctx.keyQuery }

        val batch = MsgBatch(ctx.schemaQuery, filter, nextBatchTimestamp)
        ctx.json(batch)
    }

    private val Context.topicParam: String
        get() = pathParam("topic")

    private val Context.fromQuery: Long?
        get() = queryParam("from")?.toLong()

    private val Context.keyQuery: String?
        get() = queryParam("key")

    private val Context.timeoutQuery: Duration?
        get() = queryParam("timeout")?.toLong()?.let(Duration::ofMillis)

    private val Context.schemaQuery: String
        get() = queryParam("schema") ?: throw IntrospiciereException("schema is mandatory field in query")
}