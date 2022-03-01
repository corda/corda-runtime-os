package net.corda.introspiciere.junit

import net.corda.data.demo.DemoRecord
import net.corda.introspiciere.domain.KafkaMessage
import net.corda.introspiciere.payloads.deserialize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

internal class MessageManagementTest {

    @RegisterExtension
    private val introspiciere = FakeIntrospiciereServer()

    @Test
    fun `write message`() {
        val topic = "topic1"
        val demo = DemoRecord(99)
        introspiciere.client.write(topic, "key1", demo)

        val (messages, _) =
            introspiciere.appContext.messagesGateway.readFrom(topic, DemoRecord::class.qualifiedName!!, 0)
        assertEquals(1, messages.size, "number of messages")
        assertEquals(demo, messages.first().deserialize<DemoRecord>(), "the message")
    }

    @Test
    fun `read message from beginning`() {
        val topic = "topic1"
        val key = "key1"
        val message = DemoRecord(98)
        val msg = KafkaMessage.create(topic, key, message)
        introspiciere.appContext.messagesGateway.send(topic, msg)

        val messages = introspiciere.client.readFromBeginning<DemoRecord>(topic, key)
        assertEquals(message, messages.first(), "the message")
    }

    @Test
    fun `read message from end`() {
        val topic = "topic1"
        val key = "key1"
        introspiciere.appContext.messagesGateway.send(
            topic, KafkaMessage.create(topic, key, DemoRecord(90))
        )

        val iter = introspiciere.client.readFromLatest<DemoRecord>(topic, key).iterator()
        assertNull(iter.next())

        introspiciere.appContext.messagesGateway.send(
            topic, KafkaMessage.create(topic, key, DemoRecord(91))
        )

        assertEquals(DemoRecord(91), iter.next())
    }
}