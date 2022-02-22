package net.corda.messagebus.db.consumer

import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.db.persistence.DBAccess
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class ConsumerGroupTest {
    @Test
    fun `consumer group correctly repartitions`() {
        val topic = "topic"
        val consumer1 = mock<CordaConsumer<String, String>>()
        val consumer2 = mock<CordaConsumer<String, String>>()
        val consumer3 = mock<CordaConsumer<String, String>>()
        val dbAccess = mock<DBAccess>()
        whenever(dbAccess.getTopicPartitionMap()).thenAnswer { mapOf(topic to 3) }
        val consumerGroup = ConsumerGroup("group", dbAccess)

        consumerGroup.subscribe(consumer1, setOf(topic))
        var partitions = consumerGroup.getTopicPartitionsFor(consumer1)
        assertThat(partitions.size).isEqualTo(3)

        consumerGroup.subscribe(consumer2, setOf(topic))
        consumerGroup.subscribe(consumer3, setOf(topic))
        partitions = consumerGroup.getTopicPartitionsFor(consumer1)
        assertThat(partitions.size).isEqualTo(1)
        partitions = consumerGroup.getTopicPartitionsFor(consumer2)
        assertThat(partitions.size).isEqualTo(1)
        partitions = consumerGroup.getTopicPartitionsFor(consumer3)
        assertThat(partitions.size).isEqualTo(1)
    }
}
