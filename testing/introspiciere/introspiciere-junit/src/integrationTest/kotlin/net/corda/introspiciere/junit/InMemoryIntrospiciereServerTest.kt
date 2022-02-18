package net.corda.introspiciere.junit

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class InMemoryIntrospiciereServerTest {

    companion object {
        @RegisterExtension
        @JvmStatic
        private val introspiciere = InMemoryIntrospiciereServer(
            // This only works locally at the moment. For CI it should read
            // this for an environment variable or from a config file
            kafkaBrokers = getMinikubeKafkaBroker()
        )
    }

    @Test
    fun `I can start the server with as an extension`() {
        introspiciere.client.helloWorld()
        introspiciere.client.createTopic("hola".random8)
    }
}
