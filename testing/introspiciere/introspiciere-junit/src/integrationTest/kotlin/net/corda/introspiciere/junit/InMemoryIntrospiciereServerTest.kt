package net.corda.introspiciere.junit

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class InMemoryIntrospiciereServerTest {

    companion object {
        @RegisterExtension
        @JvmStatic
        private val introspiciere = InMemoryIntrospiciereServer()
    }

    @Test
    fun `I can start the server with as an extension`() {
        introspiciere.client.helloWorld()
    }
}
