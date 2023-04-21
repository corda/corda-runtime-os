package net.corda.libs.messaging.topic.factory

import net.corda.libs.messaging.topic.utils.factory.TopicUtilsFactory
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.StringReader
import java.util.*

class KafkaTopicUtilsFactoryTest {
    private lateinit var kafkaTopicUtilsFactory: TopicUtilsFactory

    @BeforeEach
    fun beforeEach() {
        kafkaTopicUtilsFactory = KafkaTopicUtilsFactory()
    }

    @Test
    fun testCreateTopicUtils() {
        val kafkaProperties = Properties()
        kafkaProperties.load(StringReader("bootstrap.servers=localhost:9092"))
        kafkaTopicUtilsFactory.createTopicUtils(kafkaProperties).use {topicUtils ->
            Assertions.assertNotNull(topicUtils)
        }
    }
}
