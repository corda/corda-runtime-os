package net.corda.messagebus.kafka.admin

import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.ListTopicsResult
import org.apache.kafka.common.KafkaFuture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class KafkaAdminTest {

    private var adminClient = mock<AdminClient>()

    @Test
    fun `When list topics then return all none internal topics from kafka`() {
        val kafkaFuture = mock<KafkaFuture<Set<String>>>().apply {
            whenever(get()).thenReturn(setOf("topic1"))
        }
        val result = mock<ListTopicsResult>().apply {
            whenever(names()).thenReturn(kafkaFuture)
        }

        whenever(adminClient.listTopics()).thenReturn(result)

        val target = KafkaAdmin(adminClient)

        assertThat(target.getTopics()).containsOnly("topic1")
    }
}
