package net.corda.introspiciere.server

import io.javalin.Javalin
import io.javalin.http.InternalServerErrorResponse
import net.corda.introspiciere.core.CreateTopicUseCase
import net.corda.introspiciere.core.HelloWorld
import net.corda.introspiciere.core.KafkaConfig
import net.corda.introspiciere.core.KafkaConfigImpl
import net.corda.introspiciere.core.KafkaMessageGateway
import net.corda.introspiciere.core.KafkaReaderGateway
import net.corda.introspiciere.core.SimpleKafkaClient
import net.corda.introspiciere.core.addidentity.CreateKeysAndAddIdentityInteractor
import net.corda.introspiciere.core.addidentity.CryptoKeySenderImpl
import net.corda.introspiciere.domain.KafkaMessage
import java.io.Closeable
import java.net.BindException
import java.net.ServerSocket

class IntrospiciereServer(private val port: Int = 0, private val kafkaBrokers: List<String>? = null) : Closeable {

    private lateinit var app: Javalin

    fun start() {
        val thePort = if (port > 0) port else availablePort(7070)

        app = Javalin.create().start(thePort)

        val kafkaConfig: KafkaConfig = KafkaConfigImpl(
            kafkaBrokers?.joinToString(", ")
        )

        val kafka = SimpleKafkaClient(listOf(kafkaConfig.brokers))

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

        app.get("/topics/<topic>/<key>") { ctx ->
            wrapException {
                val topic = ctx.pathParam("topic")
                val key = ctx.pathParam("key")
                val schema = ctx.queryParam("schema")!!
                val messages = KafkaReaderGateway(listOf(kafkaConfig.brokers)).read(topic, key, schema)
                ctx.json(messages)
            }
        }

        app.post("/topics/<topic>", CreateTopicHandler(
            CreateTopicUseCase(kafkaConfig)
        ))


        // TODO: This one should look different
        app.put("/topics") { ctx ->
            wrapException {
                val kafkaMessage = ctx.bodyAsClass<KafkaMessage>()
                KafkaMessageGateway(listOf(kafkaConfig.brokers)).send(kafkaMessage)
                ctx.result("OK")
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

    val portUsed: Int
        get() = app.port()

    override fun close() {
        if (::app.isInitialized) app.close()
    }

    private fun availablePort(startingPort: Int): Int {
        var port = startingPort
        while (true) {
            try {
                ServerSocket(port).close()
                return port
            } catch (ex: BindException) {
                port = (port + 1) % 65_535
            }
        }
    }


}

internal fun <R> wrapException(action: () -> R): R {
    try {
        return action()
    } catch (t: Throwable) {
        throw InternalServerErrorResponse(details = mapOf("Exception" to t.stackTraceToString()))
    }
}

