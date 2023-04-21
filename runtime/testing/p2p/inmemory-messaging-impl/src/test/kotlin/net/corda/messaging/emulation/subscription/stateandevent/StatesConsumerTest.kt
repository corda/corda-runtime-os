package net.corda.messaging.emulation.subscription.stateandevent

import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.emulation.topic.model.CommitStrategy
import net.corda.messaging.emulation.topic.model.OffsetStrategy
import net.corda.messaging.emulation.topic.model.PartitionStrategy
import net.corda.messaging.emulation.topic.model.RecordMetadata
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class StatesConsumerTest {
    private val subscriptionConfig = SubscriptionConfig("group", "topic")
    private val subscription = mock<InMemoryStateAndEventSubscription<String, String, String>> {
        on { stateSubscriptionConfig } doReturn subscriptionConfig
    }
    private val stateSubscription = mock<StateSubscription<String, String>> {
        on { subscription } doReturn subscription
    }
    private val consumer = StatesConsumer(stateSubscription)

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
        assertThat(consumer.commitStrategy).isEqualTo(CommitStrategy.NO_COMMIT)
    }

    @Test
    fun `partitionStrategy is correct`() {
        assertThat(consumer.partitionStrategy).isEqualTo(PartitionStrategy.MANUAL)
    }

    @Test
    fun `partitionAssignmentListener is correct`() {
        assertThat(consumer.partitionAssignmentListener).isEqualTo(stateSubscription)
    }

    @Test
    fun `handleRecords calls subscription`() {
        val records = listOf(RecordMetadata(0L, Record("topic", "key", "value"), 1))

        consumer.handleRecords(records)

        verify(stateSubscription).gotStates(records)
    }
}
