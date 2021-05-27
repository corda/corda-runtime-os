package net.corda.libs.kafka.topic.utils

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
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
    private var adminClient: AdminClient = mock()
    private var topicResult: CreateTopicsResult = mock()
    private var kafkaFuture: KafkaFuture<Void> = mock()


    @BeforeEach
    fun beforeEach() {
        kafkaTopicUtils = KafkaTopicUtils(adminClient)
    }

    @Test
    fun testCreateTopic() {
        Mockito.`when`(adminClient.createTopics(any())).thenReturn(topicResult)
        Mockito.`when`(topicResult.all()).thenReturn(kafkaFuture)
        kafkaTopicUtils.createTopic("dummyName", 1, 1)
        verify(adminClient, times(1)).createTopics(any())
    }

    @Test
    fun testCreateTopicAlreadyExists() {
        Mockito.`when`(adminClient.createTopics(any())).thenReturn(topicResult)
        Mockito.`when`(topicResult.all()).thenReturn(kafkaFuture)
        kafkaTopicUtils.createTopic("dummyName", 1, 1)
        verify(adminClient, times(1)).createTopics(any())

        Mockito.`when`(kafkaFuture.get()).thenThrow(ExecutionException(TopicExistsException("already exists")))
        kafkaTopicUtils.createTopic("dummyName", 1, 1)
        verify(adminClient, times(2)).createTopics(any())
    }

    @Test
    fun testCreateTopicSomethingBadHappens() {
        Mockito.`when`(adminClient.createTopics(any())).thenReturn(topicResult)
        Mockito.`when`(topicResult.all()).thenReturn(kafkaFuture)
        Mockito.`when`(kafkaFuture.get()).thenThrow(ExecutionException(InterruptedException("something bad happened")))
        assertThrows<ExecutionException> { kafkaTopicUtils.createTopic("dummyName", 1, 1) }

        verify(adminClient, times(1)).createTopics(any())
    }
}
