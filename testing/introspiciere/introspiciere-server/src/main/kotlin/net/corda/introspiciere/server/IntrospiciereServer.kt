package net.corda.introspiciere.server

import io.javalin.Javalin
import net.corda.introspiciere.core.KafkaConfig
import net.corda.introspiciere.core.KafkaConfigImpl
import net.corda.introspiciere.core.TopicGateway
import net.corda.introspiciere.core.TopicGatewayImpl
import net.corda.introspiciere.domain.IntrospiciereException
import java.io.Closeable
import java.net.BindException
import java.net.ServerSocket

class IntrospiciereServer(context: Context = Context()) : Closeable {

    var app: Javalin = Javalin.create()

    init {
        val helloWorldController = HelloWorldController()
        app.get("/helloworld", helloWorldController.greeting())

        val topicController = TopicController(context)
        app.post("/topics/{topic}", topicController.create())
        app.get("/topics/{topic}/beginningOffsets", topicController.beginningOffsets())
        app.get("/topics/{topic}/endOffsets", topicController.endOffsets())

        val messagesController = MessagesController(context)
        app.get("/topics/{topic}/messages/{key}", messagesController.getAll())
        app.post("/topics/{topic}/messages/{key}", messagesController.create())
    }

    fun start(port: Int = 0) {
        val thePort = if (port > 0) port else availablePort(7070)
        app.start(thePort)
    }

    val portUsed: Int
        get() = app.port()

    override fun close() {
        app.close()
    }

    private fun availablePort(startingPort: Int): Int {
        generateSequence(startingPort) { it + 1 }.map { it % 65_535 }.forEach { port ->
            try {
                ServerSocket(port).close()
                return port
            } catch (ex: BindException) {
                /* do nothing */
            }
        }
        throw IntrospiciereException("Should never reach this point")
    }
}

data class Context(
    val kafkaConfig: KafkaConfig = KafkaConfigImpl(),
    val topicGateway: TopicGateway = TopicGatewayImpl(kafkaConfig),
)

