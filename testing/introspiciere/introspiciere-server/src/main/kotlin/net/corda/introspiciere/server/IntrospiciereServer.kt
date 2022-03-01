package net.corda.introspiciere.server

import io.javalin.Javalin
import net.corda.introspiciere.domain.IntrospiciereException
import java.io.Closeable
import java.net.BindException
import java.net.ServerSocket

class IntrospiciereServer(appContext: AppContext = DefaultAppContext()) : Closeable {

    var app: Javalin = Javalin.create()

    init {
        val helloWorldController = HelloWorldController()
        app.get("/helloworld", helloWorldController.greeting())

        val topicController = TopicController(appContext)
        app.post("/topics", topicController.create()) // This one is wrong
        app.get("/topics", topicController.getAll())
        app.get("/topics/{topic}", topicController.getByName())
        app.delete("/topics/{topic}", topicController.delete())

        val messagesController = MessagesController(appContext)
        app.get("/topics/{topic}/messages", messagesController.getMessages())
        app.post("/topics/{topic}/messages", messagesController.writeMessage())
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
