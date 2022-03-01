package net.corda.introspiciere.junit

import net.corda.introspiciere.server.AppContext
import net.corda.introspiciere.server.IntrospiciereServer
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class FakeIntrospiciereServer(
    private val port: Int = 0,
) : BeforeEachCallback, AfterEachCallback {

    override fun beforeEach(context: ExtensionContext?) = startServer()
    override fun afterEach(context: ExtensionContext?) = stopServer()

    val appContext: AppContext = FakeAppContext()

    private val server: IntrospiciereServer = IntrospiciereServer(appContext)

    private fun startServer() {
        server.start(port)
    }

    private fun stopServer() {
        server.close()
    }

    /**
     * Endpoint where Introspiciere is listening.
     */
    val endpoint: String
        get() = "http://localhost:${server.portUsed}"

    /**
     * Introspiciere client.
     */
    val client: IntrospiciereClient
        get() = IntrospiciereClient(endpoint)
}