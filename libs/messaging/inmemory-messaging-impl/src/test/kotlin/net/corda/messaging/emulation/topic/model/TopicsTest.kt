package net.corda.messaging.emulation.topic.model

import net.corda.messaging.api.records.Record
import net.corda.messaging.emulation.properties.InMemoryConfiguration
import net.corda.messaging.emulation.properties.SubscriptionConfiguration
import net.corda.messaging.emulation.properties.TopicConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.Duration

class TopicsTest {
    private val config = mock<InMemoryConfiguration> {
        on { topicConfiguration(any()) } doReturn TopicConfiguration(1, 1)
        on { subscriptionConfiguration(any()) } doReturn SubscriptionConfiguration(1, Duration.ofMillis(2))
    }
    private val topics = Topics(config)

    @Test
    fun `getTopic return a new topic when needed`() {
        val topic = topics.getTopic("topic")

        assertThat(topic).isNotNull
    }

    @Test
    fun `getTopic return the same topic when can`() {
        val topic1 = topics.getTopic("topic")
        val topic2 = topics.getTopic("topic")

        assertThat(topic1).isEqualTo(topic2)
    }

    @Test
    fun `getTopic return the different topic when cant`() {
        val topic1 = topics.getTopic("topic1")
        val topic2 = topics.getTopic("topic2")

        assertThat(topic1).isNotEqualTo(topic2)
    }

    @Test
    fun `createConsumption return valid thread`() {
        val consumer = mock<Consumer> {
            on { groupName } doReturn "group"
            on { topicName } doReturn "topic"
            on { partitionStrategy } doReturn PartitionStrategy.SHARE_PARTITIONS
            on { commitStrategy } doReturn CommitStrategy.COMMIT_AFTER_PROCESSING
            on { offsetStrategy } doReturn OffsetStrategy.EARLIEST
        }
        val thread = topics.createConsumption(consumer)

        assertThat(thread).isNotNull
    }

    @Test
    fun `getWriteLock create the correct lock`() {
        val records = (1..4).flatMap { topicNumber ->
            (1..3).map { key ->
                Record("topic$topicNumber", key, "value")
            }
        }
        Mockito.mockConstruction(PartitionsWriteLock::class.java) { _, context ->
            val partitions = context.arguments()[0] as Collection<Any?>
            assertThat(partitions.filterIsInstance<Partition>().map { it.topicName })
                .hasSize(records.size)
                .contains("topic1", "topic2", "topic3", "topic4")
        }.use {

            topics.getWriteLock(records)
        }
    }

    @Test
    fun `getWriteLock with partition IDcreate the correct lock `() {
        val records = (1..4).flatMap { topicNumber ->
            (1..3).map { key ->
                Record("topic$topicNumber", key, "value")
            }
        }
        Mockito.mockConstruction(PartitionsWriteLock::class.java) { _, context ->
            val partitions = context.arguments()[0] as Collection<Any?>
            assertThat(partitions.filterIsInstance<Partition>().map { it.topicName })
                .hasSize(records.size)
                .contains("topic1", "topic2", "topic3", "topic4")
        }.use {

            topics.getWriteLock(records, 1)
        }
    }
}
