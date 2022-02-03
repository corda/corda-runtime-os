package net.corda.introspiciere.server

import io.javalin.Javalin
import io.javalin.http.InternalServerErrorResponse
import net.corda.introspiciere.core.HelloWorld
import net.corda.introspiciere.core.SimpleKafkaClient
import net.corda.introspiciere.core.addidentity.CreateKeysAndAddIdentityInteractor
import net.corda.introspiciere.core.addidentity.CryptoKeySenderImpl

fun main() {
    val app = Javalin.create().start(7070)
    val servers = System.getenv("KAFKA_BROKERS")?.split(",") ?: listOf("alpha-bk-1:9092")
    val kafka = SimpleKafkaClient(servers)

    app.get("/helloworld") { ctx ->
        wrapException {
            val greeting = HelloWorld().greeting()
            ctx.result(greeting)
        }
    }

    app.get("/topics") { ctx ->
        wrapException {
            val topics = kafka.fetchTopics()
            ctx.result(topics)
        }
    }

    app.post("/identities") { ctx ->
        wrapException {
            val input = ctx.bodyAsClass<CreateKeysAndAddIdentityInteractor.Input>()
            CreateKeysAndAddIdentityInteractor(CryptoKeySenderImpl(kafka)).execute(input)
            ctx.result("OK")
        }
    }
}

private fun <R> wrapException(action: () -> R): R {
    try {
        return action()
    } catch (t: Throwable) {
        throw InternalServerErrorResponse(details = mapOf("Exception" to t.stackTraceToString()))
    }
}