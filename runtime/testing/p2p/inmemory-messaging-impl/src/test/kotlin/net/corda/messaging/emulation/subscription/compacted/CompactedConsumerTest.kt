package net.corda.messaging.emulation.subscription.compacted

import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
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
import java.net.URI

class CompactedConsumerTest {
    private val mockProcessor = mock<CompactedProcessor<String, URI>> {
        on { keyClass } doReturn String::class.java
        on { valueClass } doReturn URI::class.java
    }
    private val subscription = mock<InMemoryCompactedSubscription<String, URI>> {
        on { groupName } doReturn "group"
        on { topicName } doReturn "topic"
        on { processor } doReturn mockProcessor
    }
    private val consumer = CompactedConsumer(subscription)

    @Test
    fun `groupName returns the group name`() {
        assertThat(consumer.groupName).startsWith("group")
    }

    @Test
    fun `topicName returns the topic name`() {
        assertThat(consumer.topicName).isEqualTo("topic")
    }

    @Test
    fun `offsetStrategy returns the earliest`() {
        assertThat(consumer.offsetStrategy).isEqualTo(OffsetStrategy.EARLIEST)
    }

    @Test
    fun `partition assigngnment listener will be empty`() {
        assertThat(consumer.partitionAssignmentListener as PartitionAssignmentListener?).isNull()
    }

    @Test
    fun `handleRecords will send the correct records to the subscriber`() {
        val records = listOf(
            RecordMetadata(
                offset = 1L,
                partition = 12,
                record = Record("topic", "key1", "value")
            ),
            RecordMetadata(
                offset = 2L,
                partition = 13,
                record = Record("topic", 44L, URI.create("https://www.r3.com/"))
            ),
            RecordMetadata(
                offset = 2L,
                partition = 13,
                record = Record("topic", "key3", URI.create("https://www.r3.com/"))
            ),
            RecordMetadata(
                offset = 2L,
                partition = 13,
                record = Record("topic", "key4", URI.create("https://www.corda.net/"))
            ),
        )

        consumer.handleRecords(records)

        records.forEach {
            verify(subscription).onNewRecord(
                it
            )
        }
    }

    @Test
    fun `commitStrategy is correct`() {
        assertThat(consumer.commitStrategy).isEqualTo(CommitStrategy.NO_COMMIT)
    }

    @Test
    fun `partitionStrategy is correct`() {
        assertThat(consumer.partitionStrategy).isEqualTo(PartitionStrategy.SHARE_PARTITIONS)
    }
}
