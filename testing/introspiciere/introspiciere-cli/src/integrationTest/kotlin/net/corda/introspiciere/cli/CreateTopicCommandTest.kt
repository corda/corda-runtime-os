package net.corda.introspiciere.cli

import net.corda.introspiciere.junit.InMemoryIntrospiciereServer
import net.corda.introspiciere.junit.getMinikubeKafkaBroker
import net.corda.introspiciere.junit.random8
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class CreateTopicCommandTest {

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
    fun `I can create a topic from cli`() {
        internalMain(
            "create-topic",
            "--endpoint", introspiciere.endpoint,
            "--topic", "topic".random8,
            "--partitions", "3",
            "--replication-factor", "2"
        )
    }
}