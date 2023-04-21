package net.corda.messaging.emulation.subscription.stateandevent

import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.emulation.topic.model.CommitStrategy
import net.corda.messaging.emulation.topic.model.OffsetStrategy
import net.corda.messaging.emulation.topic.model.PartitionStrategy
import net.corda.messaging.emulation.topic.model.RecordMetadata
import net.corda.messaging.emulation.topic.service.TopicService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class EventConsumerTest {
    private val stateConsumer = mock<StatesConsumer<String, String>>()
    private val stateSubscription = mock<StateSubscription<String, String>> {
        on { consumer } doReturn stateConsumer
    }
    private val topicService = mock<TopicService>()
    private val subscriptionConfig = SubscriptionConfig("group", "topic")
    private val subscription = mock<InMemoryStateAndEventSubscription<String, String, String>> {
        on { subscriptionConfig } doReturn subscriptionConfig
        on { stateSubscription } doReturn stateSubscription
        on { topicService } doReturn topicService
    }
    private val eventsSubscription = mock<EventSubscription<String, String, String>> {
        on { subscription } doReturn subscription
    }
    private val consumer = EventConsumer(eventsSubscription)

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
        assertThat(consumer.commitStrategy).isEqualTo(CommitStrategy.COMMIT_AFTER_PROCESSING)
    }

    @Test
    fun `partitionStrategy is correct`() {
        assertThat(consumer.partitionStrategy).isEqualTo(PartitionStrategy.DIVIDE_PARTITIONS)
    }

    @Test
    fun `partitionAssignmentListener assign the correct partitions to the state consumer`() {
        consumer.partitionAssignmentListener.onPartitionsAssigned(listOf("topic" to 1, "topic" to 5))

        verify(topicService).manualAssignPartitions(stateConsumer, listOf(1, 5))
    }

    @Test
    fun `partitionAssignmentListener un assign the correct partitions to the state consumer`() {
        consumer.partitionAssignmentListener.onPartitionsUnassigned(listOf("topic" to 2, "topic" to 4))

        verify(topicService).manualUnAssignPartitions(stateConsumer, listOf(2, 4))
    }

    @Test
    fun `handleRecords calls subscription`() {
        val records = listOf(RecordMetadata(0L, Record("topic", "key", "value"), 1))

        consumer.handleRecords(records)

        verify(eventsSubscription).processEvents(records)
    }
}
