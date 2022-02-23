package net.corda.introspiciere.server

import io.javalin.Javalin
import net.corda.introspiciere.core.KafkaConfig
import net.corda.introspiciere.core.KafkaConfigImpl
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

        val helloWorldController = HelloWorldController()
        app.get("/helloworld", helloWorldController.greeting())

        val topicController = TopicController(kafkaConfig)
        app.post("/topics/{topic}", topicController.create())

        val messagesController = MessagesController(kafkaConfig)
        app.get("/topics/{topic}/messages/{key}", messagesController.getAll())
        app.post("/topics/{topic}/messages/{key}", messagesController.create())
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
