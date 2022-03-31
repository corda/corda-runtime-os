package net.corda.libs.messaging.topic

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.CreateTopicsResult
import org.apache.kafka.common.KafkaFuture
import org.apache.kafka.common.errors.TopicExistsException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.util.concurrent.ExecutionException

class KafkaTopicUtilsTest {
    private lateinit var kafkaTopicUtils: KafkaTopicUtils
    private lateinit var adminClient: AdminClient
    private lateinit var topicResult: CreateTopicsResult
    private lateinit var kafkaFuture: KafkaFuture<Void>


    @BeforeEach
    fun beforeEach() {
        adminClient = mock()
        topicResult = mock()
        kafkaFuture = mock()
        kafkaTopicUtils = KafkaTopicUtils(adminClient)
    }

    @Test
    fun testCreateTopics() {
        Mockito.`when`(adminClient.createTopics(any())).thenReturn(topicResult)
        Mockito.`when`(topicResult.all()).thenReturn(kafkaFuture)
        kafkaTopicUtils.createTopics(dummyTopicConfig())
        verify(adminClient, times(1)).createTopics(any())
    }

    @Test
    fun testCreateTopicAlreadyExists() {
        Mockito.`when`(adminClient.createTopics(any())).thenReturn(topicResult)
        Mockito.`when`(topicResult.all()).thenReturn(kafkaFuture)
        kafkaTopicUtils.createTopics(dummyTopicConfig())
        verify(adminClient, times(1)).createTopics(any())

        Mockito.`when`(kafkaFuture.get()).thenThrow(ExecutionException(TopicExistsException("already exists")))
        kafkaTopicUtils.createTopics(dummyTopicConfig())
        verify(adminClient, times(2)).createTopics(any())
    }

    @Test
    fun testCreateTopicSomethingBadHappens() {
        Mockito.`when`(adminClient.createTopics(any())).thenReturn(topicResult)
        Mockito.`when`(topicResult.all()).thenReturn(kafkaFuture)
        Mockito.`when`(kafkaFuture.get()).thenThrow(ExecutionException(InterruptedException("something bad happened")))
        assertThrows<InterruptedException> { kafkaTopicUtils.createTopics(dummyTopicConfig()) }

        verify(adminClient, times(1)).createTopics(any())
    }

    private fun dummyTopicConfig(): Config = ConfigFactory.parseString(
        """
        topics = [
            {
                topicName = "dummyName"
                numPartitions = 1
                replicationFactor = 1
                config {
                    first.key = "firstValue",
                    second.key = "secondValue"
                }
            }
        ]
    """.trimIndent()
    )
}
