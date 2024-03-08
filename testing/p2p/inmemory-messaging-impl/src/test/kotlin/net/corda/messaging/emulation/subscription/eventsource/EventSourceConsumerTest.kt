package net.corda.messaging.emulation.subscription.eventsource

import net.corda.messaging.api.processor.EventSourceProcessor
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.emulation.topic.model.CommitStrategy
import net.corda.messaging.emulation.topic.model.OffsetStrategy
import net.corda.messaging.emulation.topic.model.PartitionStrategy
import net.corda.messaging.emulation.topic.model.RecordMetadata
import net.corda.messaging.emulation.topic.service.TopicService
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

class EventSourceConsumerTest {
    private val processor = mock< EventSourceProcessor<String, SubscriptionConfig>> {
        on { keyClass } doReturn String::class.java
        on { valueClass } doReturn SubscriptionConfig::class.java
    }
    private val topicService = mock<TopicService>()
    private val subscription = mock<EventSourceSubscription<String, SubscriptionConfig>> {
        on { processor } doReturn processor
        on { subscriptionConfig } doReturn SubscriptionConfig("group", "topic")
        on { partitionAssignmentListener } doReturn mock()
        on { topicService } doReturn topicService
    }
    private val consumer = EventSourceConsumer(subscription)

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
        Assertions.assertThat(consumer.groupName).isEqualTo("group")
    }

    @Test
    fun `topicName is correct`() {
        Assertions.assertThat(consumer.topicName).isEqualTo("topic")
    }

    @Test
    fun `offsetStrategy is correct`() {
        Assertions.assertThat(consumer.offsetStrategy).isEqualTo(OffsetStrategy.EARLIEST)
    }

    @Test
    fun `commitStrategy is correct`() {
        Assertions.assertThat(consumer.commitStrategy).isEqualTo(CommitStrategy.COMMIT_AFTER_PROCESSING)
    }

    @Test
    fun `partitionStrategy is correct`() {
        Assertions.assertThat(consumer.partitionStrategy).isEqualTo(PartitionStrategy.DIVIDE_PARTITIONS)
    }

    @Test
    fun `partitionAssignmentListener is correct`() {
        Assertions.assertThat(consumer.partitionAssignmentListener).isEqualTo(subscription.partitionAssignmentListener)
    }
}
