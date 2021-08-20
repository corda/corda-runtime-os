package net.corda.messaging.emulation.subscription.eventlog

import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.emulation.topic.model.CommitStrategy
import net.corda.messaging.emulation.topic.model.OffsetStrategy
import net.corda.messaging.emulation.topic.model.PartitionStrategy
import net.corda.messaging.emulation.topic.model.RecordMetadata
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class EventLogConsumerTest {
    private val processor = mock< EventLogProcessor<String, SubscriptionConfig>> {
        on { keyClass } doReturn String::class.java
        on { valueClass } doReturn SubscriptionConfig::class.java
    }
    private val subscription = mock<EventLogSubscription<String, SubscriptionConfig>> {
        on { processor } doReturn processor
        on { groupName } doReturn "group"
        on { topicName } doReturn "topic"
        on { partitionAssignmentListener } doReturn mock()
    }
    private val consumer = EventLogConsumer(subscription)

    @Test
    fun `handleRecords sends the correct records to the processor`() {
        val records = listOf(
            RecordMetadata(
                offset = 33L,
                partition = 5,
                record = Record("topic", "key", "value")
            ),
            RecordMetadata(
                offset = 241,
                partition = 5,
                record = Record("topic", 44, SubscriptionConfig("a", "b"))
            ),
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

        verify(processor).onNext(
            listOf(
                EventLogRecord(topic = "topic", key = "key4", value = SubscriptionConfig("c", "d"), partition = 1, offset = 21),
                EventLogRecord(topic = "topic", key = "key6", value = SubscriptionConfig("c", "d"), partition = 3, offset = 100),
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
        assertThat(consumer.offsetStrategy).isEqualTo(OffsetStrategy.EARLIEST)
    }

    @Test
    fun `commitStrategy is correct`() {
        assertThat(consumer.commitStrategy).isEqualTo(CommitStrategy.AUTO_COMMIT)
    }

    @Test
    fun `partitionStrategy is correct`() {
        assertThat(consumer.partitionStrategy).isEqualTo(PartitionStrategy.DIVIDE_PARTITIONS)
    }

    @Test
    fun `partitionAssignmentListener is correct`() {
        assertThat(consumer.partitionAssignmentListener).isEqualTo(subscription.partitionAssignmentListener)
    }
}
