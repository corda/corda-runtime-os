package net.corda.introspiciere.core

import net.corda.data.demo.DemoRecord
import net.corda.introspiciere.domain.KafkaMessage
import net.corda.introspiciere.domain.TopicDefinition
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.time.Duration
import java.time.Duration.ZERO
import java.time.Instant

internal class MessagesGatewayTest {
    private val kafkaConfig = object : KafkaConfig {
        override val brokers: String = "20.62.51.171:9094"
    }
    private lateinit var topicGateway: TopicGateway
    private lateinit var messagesGateway: MessagesGateway
    private lateinit var topic: String

    @BeforeEach
    fun beforeEach() {
        messagesGateway = MessagesGatewayImpl(kafkaConfig)
        topicGateway = TopicGatewayImpl(kafkaConfig)

        topic = "topic-" + Instant.now().toEpochMilli()
        topicGateway.create(TopicDefinition(topic))
    }

    @AfterEach
    fun afterEach() {
        topicGateway.removeByName(topic)
    }

    @Test
    fun `I can send a message`() {
        messagesGateway.send(topic, KafkaMessage.create(topic, "key1", DemoRecord(33)))
    }

    @Test
    fun `read from beginning`() {
        val demos = listOf(DemoRecord(20), DemoRecord(21))
        messagesGateway.send(topic, KafkaMessage.create(topic, "key1", demos[0]))
        messagesGateway.send(topic, KafkaMessage.create(topic, "key1", demos[1]))

        val (msgs, _) = messagesGateway.readFrom(topic, DemoRecord::class.qualifiedName!!, 0, ZERO)
        val actualDemos = msgs.map { DemoRecord.fromByteBuffer(ByteBuffer.wrap(it.data)) }
        assertEquals(demos, actualDemos, "same demo records")
    }

    @Test
    fun `read empty topic returns timestasmp and read from timestamp`() {
        val (_, timestamp) = messagesGateway.readFrom(topic, DemoRecord::class.qualifiedName!!, Instant.now().toEpochMilli(), ZERO)

        val demos = listOf(DemoRecord(20), DemoRecord(21))
        messagesGateway.send(topic, KafkaMessage.create(topic, "key1", demos[0]))
        messagesGateway.send(topic, KafkaMessage.create(topic, "key1", demos[1]))

        val (msgs, _) = messagesGateway.readFrom(topic, DemoRecord::class.qualifiedName!!, timestamp, ZERO)
        val actualDemos = msgs.map { DemoRecord.fromByteBuffer(ByteBuffer.wrap(it.data)) }
        assertEquals(demos, actualDemos, "same demo records")
    }
}