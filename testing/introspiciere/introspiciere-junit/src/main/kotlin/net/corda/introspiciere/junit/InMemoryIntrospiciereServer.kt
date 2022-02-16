package net.corda.introspiciere.junit

import net.corda.introspiciere.server.IntrospiciereServer
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class InMemoryIntrospiciereServer(
    private val port: Int = 0,
    private val kafkaBrokers: List<String>? = null,
) : BeforeAllCallback, BeforeEachCallback, AfterEachCallback, AfterAllCallback {

    override fun beforeEach(context: ExtensionContext?) = startServer()
    override fun beforeAll(context: ExtensionContext?) = startServer()
    override fun afterEach(context: ExtensionContext?) = stopServer()
    override fun afterAll(context: ExtensionContext?) = stopServer()

    private val server = IntrospiciereServer(port, kafkaBrokers)

    private fun startServer() {
        server.start()
    }

    private fun stopServer() {
        server.close()
    }

    val client: IntrospiciereClient
        get() = IntrospiciereClient("http://localhost:$port")
}

class IntrospiciereClient(private val endpoint: String) {
    fun helloWorld() {
        println("I should call $endpoint/helloworld")
    }
}
