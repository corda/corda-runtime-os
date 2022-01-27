package net.corda.introspiciere.server

import io.javalin.Javalin
import io.javalin.http.InternalServerErrorResponse
import net.corda.introspiciere.core.CreateKeysAndAddIdentityInteractor
import net.corda.introspiciere.core.HelloWorld
import net.corda.introspiciere.core.SimpleKafkaClient

fun main() {
    val app = Javalin.create().start(7070)

    val servers = System.getenv("KAFKA_BROKERS")?.split(",") ?: listOf("kafka-service:9092")
    val kafka = SimpleKafkaClient(servers)

    app.get("/helloworld") { ctx ->
        try {
            val greeting = HelloWorld().greeting()
            ctx.result(greeting)
        } catch (ex: Exception) {
            throw InternalServerErrorResponse(details = mapOf(
                "exception" to ex.stackTraceToString()
            ))
        }
    }

    app.post("/identities") { ctx ->
        val input = ctx.bodyAsClass<CreateKeysAndAddIdentityInteractor.Input>()
        CreateKeysAndAddIdentityInteractor(kafka).execute(input)
        ctx.result("OK")
    }
}