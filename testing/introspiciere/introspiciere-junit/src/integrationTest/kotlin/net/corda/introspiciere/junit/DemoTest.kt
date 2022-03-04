package net.corda.introspiciere.junit

import net.corda.data.demo.DemoRecord
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

class DemoTest {

    companion object {
        @RegisterExtension
        @JvmStatic
        private val introspiciere = InMemoryIntrospiciereServer(
            kafkaBrokers = "20.62.51.171:9094"
        )
    }

    private lateinit var topic1: String
    private lateinit var topic2: String
    private val key = "key1"

    @BeforeEach
    fun beforeEach() {
        topic1 = "topic1".random8
        topic2 = "topic2".random8
        introspiciere.client.createTopic(topic1, 5, 2)
        introspiciere.client.createTopic(topic2, 5, 2)
    }

    @AfterEach
    fun afterEach() {
        introspiciere.client.deleteTopic(topic1)
        introspiciere.client.deleteTopic(topic2)
    }

    @Test
    @Timeout(60)
    fun `send and receive messages`() {

        // Create reader
        val reader = introspiciere.client.readFromLatest<DemoRecord>(topic1, key).iterator()

        // Write values
        repeat(10) {
            introspiciere.client.write(topic1, key, DemoRecord(it))
        }

        // Read values
        var counter = 0
        while (counter < 10) {
            val next = reader.next() ?: continue
            assertEquals(DemoRecord(counter), next)
            counter += 1
        }
    }

    @Test
    @Timeout(120)
    fun `kafka consumer in a can`() {
        // Transform data
        introspiciere.client.handle<DemoRecord>(topic1, key) { client, demoRecord ->
            client.write(topic2, key, DemoRecord(demoRecord.value * -1))
        }

        // Create reader
        val reader = introspiciere.client.readFromLatest<DemoRecord>(topic2, key).iterator()

        // Write values
        repeat(10) {
            introspiciere.client.write(topic1, key, DemoRecord(it))
        }

        var counter = 0
        while (counter < 10) {
            val next = reader.next() ?: continue
            assertEquals(DemoRecord(counter * -1), next)
            counter += 1
        }
    }
}