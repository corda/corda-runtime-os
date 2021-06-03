package net.corda.libs.kafka.topic.utils.impl

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
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
import java.util.concurrent.ExecutionException

class KafkaTopicUtilsTest {
    private lateinit var kafkaTopicUtils: KafkaTopicUtils
    private val adminClient: AdminClient = mock()
    private val topicResult: CreateTopicsResult = mock()
    private val kafkaFuture: KafkaFuture<Void> = mock()


    @BeforeEach
    fun beforeEach() {
        kafkaTopicUtils = KafkaTopicUtils(adminClient)
    }

    @Test
    fun testCreateTopic() {
        Mockito.`when`(adminClient.createTopics(any())).thenReturn(topicResult)
        Mockito.`when`(topicResult.all()).thenReturn(kafkaFuture)
        kafkaTopicUtils.createTopic(dummyTopicConfig())
        verify(adminClient, times(1)).createTopics(any())
    }

    @Test
    fun testCreateTopicAlreadyExists() {
        Mockito.`when`(adminClient.createTopics(any())).thenReturn(topicResult)
        Mockito.`when`(topicResult.all()).thenReturn(kafkaFuture)
        kafkaTopicUtils.createTopic(dummyTopicConfig())
        verify(adminClient, times(1)).createTopics(any())

        Mockito.`when`(kafkaFuture.get()).thenThrow(ExecutionException(TopicExistsException("already exists")))
        kafkaTopicUtils.createTopic(dummyTopicConfig())
        verify(adminClient, times(2)).createTopics(any())
    }

    @Test
    fun testCreateTopicSomethingBadHappens() {
        Mockito.`when`(adminClient.createTopics(any())).thenReturn(topicResult)
        Mockito.`when`(topicResult.all()).thenReturn(kafkaFuture)
        Mockito.`when`(kafkaFuture.get()).thenThrow(ExecutionException(InterruptedException("something bad happened")))
        assertThrows<InterruptedException> { kafkaTopicUtils.createTopic(dummyTopicConfig()) }

        verify(adminClient, times(1)).createTopics(any())
    }

    private fun dummyTopicConfig(): Config = ConfigFactory.parseString(
        """
        topicName = "dummyName"
        numPartitions = 1
        replicationFactor = 1
        config {
            first.key = "firstValue",
            second.key = "secondValue"
        }
    """.trimIndent()
    )
}
