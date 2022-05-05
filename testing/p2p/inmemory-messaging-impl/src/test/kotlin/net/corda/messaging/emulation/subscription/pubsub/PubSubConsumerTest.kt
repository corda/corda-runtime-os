package net.corda.messaging.emulation.subscription.pubsub

import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.listener.PartitionAssignmentListener
import net.corda.messaging.emulation.topic.model.CommitStrategy
import net.corda.messaging.emulation.topic.model.OffsetStrategy
import net.corda.messaging.emulation.topic.model.PartitionStrategy
import net.corda.messaging.emulation.topic.model.RecordMetadata
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class PubSubConsumerTest {
    private val subscription = mock<PubSubSubscription<String, SubscriptionConfig>> {
        on { subscriptionConfig } doReturn SubscriptionConfig("group", "topic")
    }
    private val consumer = PubSubConsumer(subscription)

    @Test
    fun `handleRecords sends the correct records to the processor`() {
        val records = listOf(
            RecordMetadata(
                offset = 21,
                partition = 1,
                record = Record("topic", "key4", SubscriptionConfig("c", "d"))
            ),
            RecordMetadata(
                offset = 100,
                partition = 3,
                record = Record("topic", "key6", SubscriptionConfig("c", "d"))
            ),
        )

        consumer.handleRecords(records)

        verify(subscription).processRecords(
            listOf(
                RecordMetadata(
                    offset = 21,
                    partition = 1,
                    record = Record("topic", "key4", SubscriptionConfig("c", "d")),
                ),
                RecordMetadata(
                    offset = 100,
                    partition = 3,
                    record = Record("topic", "key6", SubscriptionConfig("c", "d")),
                ),
            )
        )
    }

    @Test
    fun `groupName is correct`() {
        assertThat(consumer.groupName).isEqualTo("group")
    }

    @Test
    fun `topicName is correct`() {
        assertThat(consumer.topicName).isEqualTo("topic")
    }

    @Test
    fun `offsetStrategy is correct`() {
        assertThat(consumer.offsetStrategy).isEqualTo(OffsetStrategy.LATEST)
    }

    @Test
    fun `commitStrategy is correct`() {
        assertThat(consumer.commitStrategy).isEqualTo(CommitStrategy.NO_COMMIT)
    }

    @Test
    fun `partitionStrategy is correct`() {
        assertThat(consumer.partitionStrategy).isEqualTo(PartitionStrategy.DIVIDE_PARTITIONS)
    }

    @Test
    fun `partitionAssignmentListener is correct`() {
        assertThat(consumer.partitionAssignmentListener as PartitionAssignmentListener?).isNull()
    }
}
