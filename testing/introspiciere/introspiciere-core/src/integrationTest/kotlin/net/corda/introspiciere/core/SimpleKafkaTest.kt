package net.corda.introspiciere.core

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class SimpleKafkaTest {

    @RegisterExtension
    val kafka = DeployKafka("alpha")

    @Test
    fun first() {
        kafka.client.createTopic("topic1")
        kafka.client.send("topic1", "key", "value1")
        val messages = kafka.client.read<Void, String>("topic1")
        Assertions.assertEquals(listOf("value1"), messages)
    }
}