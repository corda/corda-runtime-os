package net.corda.messagebus.kafka.admin

import net.corda.v5.base.exceptions.CordaRuntimeException
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.ListTopicsResult
import org.apache.kafka.common.KafkaFuture
import org.apache.kafka.common.errors.TimeoutException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.ExecutionException

class KafkaAdminTest {

    @Test
    fun `listTopics returns all internal topics from kafka`() {
        var adminClient = mock<AdminClient>()

        val kafkaFuture = mock<KafkaFuture<Set<String>>>().apply {
            whenever(get()).thenReturn(setOf("topic1"))
        }
        val result = mock<ListTopicsResult>().apply {
            whenever(names()).thenReturn(kafkaFuture)
        }

        whenever(adminClient.listTopics()).thenReturn(result)

        val admin = KafkaAdmin(adminClient)

        assertThat(admin.getTopics()).containsOnly("topic1")
    }

    @Test
    fun `getTopics will retry an exception and be successful when retries not exceeded`() {
        val adminClient = mock<AdminClient>()
        val kafkaFuture = mock<KafkaFuture<Set<String>>>()

        val topicResult = mock<ListTopicsResult>()
        whenever(topicResult.names()).thenReturn(kafkaFuture)
        whenever(adminClient.listTopics()).thenReturn(topicResult)

        //retries hardcoded in getTopics to max 3 attempts

        whenever(kafkaFuture.get())
            .thenThrow(ExecutionException(TimeoutException("timed out")))
            .thenThrow(ExecutionException(TimeoutException("timed out")))
            .thenReturn(setOf("topic1"))

        val admin = KafkaAdmin(adminClient)

        assertThat(admin.getTopics()).containsOnly("topic1")
    }

    @Test
    fun `getTopics will retry an exception and rethrow when retries exceeded`() {
        val adminClient = mock<AdminClient>()
        val kafkaFuture = mock<KafkaFuture<Set<String>>>()

        val topicResult = mock<ListTopicsResult>()
        whenever(topicResult.names()).thenReturn(kafkaFuture)
        whenever(adminClient.listTopics()).thenReturn(topicResult)

        //retries hardcoded in getTopics to max 3 attempts

        whenever(kafkaFuture.get())
            .thenThrow(ExecutionException(TimeoutException("timed out")))

        val admin = KafkaAdmin(adminClient)

        assertThrows<CordaRuntimeException> { admin.getTopics() }
        verify(kafkaFuture, times(3)).get()
    }
}
