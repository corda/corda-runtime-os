package net.corda.messaging.emulation.topic.model

import net.corda.messaging.api.records.Record
import net.corda.messaging.emulation.properties.SubscriptionConfiguration
import net.corda.messaging.emulation.properties.TopicConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TopicTest {
    private val config = TopicConfiguration(5, 10)

    @Test
    fun `subscribe will subscribe to the correct group`() {
        val topic = Topic("topic", config)
        mockConstruction(ConsumerGroup::class.java).use { group ->
            val subscriptionConfig = SubscriptionConfiguration(10, 100L)
            val consumer = mock<Consumer> {
                on { groupName } doReturn "group"
            }

            topic.subscribe(consumer, subscriptionConfig)

            verify(group.constructed().first()).subscribe(consumer)
        }
    }

    @Test
    fun `subscribe will create a group with the correct arguments`() {
        val topic = Topic("topic", config)
        mockConstruction(ConsumerGroup::class.java) { _, context ->
            assertThat(context.arguments()[1] as Collection<Any?>).hasSize(5)
        }.use {
            val subscriptionConfig = SubscriptionConfiguration(10, 100L)
            val consumer = mock<Consumer> {
                on { groupName } doReturn "group"
            }

            topic.subscribe(consumer, subscriptionConfig)
        }
    }

    @Test
    fun `unsubscribe will unsubscribe to the correct group`() {
        val topic = Topic("topic", config)
        mockConstruction(ConsumerGroup::class.java).use { group ->
            val subscriptionConfig = SubscriptionConfiguration(10, 100L)
            val consumer = mock<Consumer> {
                on { groupName } doReturn "group"
            }
            topic.subscribe(consumer, subscriptionConfig)

            topic.unsubscribe(consumer)

            verify(group.constructed().first()).unsubscribe(consumer)
        }
    }

    @Test
    fun `unsubscribe will not unsubscribe unknown consumer`() {
        val topic = Topic("topic", config)
        mockConstruction(ConsumerGroup::class.java).use { group ->
            val subscriptionConfig = SubscriptionConfiguration(10, 100L)
            val consumer1 = mock<Consumer> {
                on { groupName } doReturn "group"
            }
            val consumer2 = mock<Consumer> {
                on { groupName } doReturn "group2"
            }
            topic.subscribe(consumer1, subscriptionConfig)

            topic.unsubscribe(consumer2)

            verify(group.constructed().first(), never()).unsubscribe(any())
        }
    }

    @Test
    fun `addRecord will add record to the correct partition`() {
        mockConstruction(Partition::class.java).use { partitions ->
            val topic = Topic("topic", config)
            val record = Record("topic", 1004, 3)

            topic.addRecord(record)

            verify(partitions.constructed()[4]).addRecord(record)
        }
    }

    @Test
    fun `addRecord will not add record to the wrong partition`() {
        mockConstruction(Partition::class.java).use { partitions ->
            val topic = Topic("topic", config)
            val record = Record("topic", 1004, 3)

            topic.addRecord(record)

            verify(partitions.constructed()[3], never()).addRecord(any())
        }
    }

    @Test
    fun `addRecord will wake up any group`() {
        val topic = Topic("topic", config)
        mockConstruction(ConsumerGroup::class.java).use { group ->
            val subscriptionConfig = SubscriptionConfiguration(10, 100L)
            val consumer = mock<Consumer> {
                on { groupName } doReturn "group"
            }
            topic.subscribe(consumer, subscriptionConfig)
            val record = Record("topic", 1004, 3)

            topic.addRecord(record)

            verify(group.constructed().first()).wakeUp()
        }
    }

    @Test
    fun `addRecordToPartition will add record to the correct partition`() {
        mockConstruction(
            Partition::class.java
        ) { mock, settings ->
            whenever(mock.partitionId).thenReturn(settings.count)
        }.use { partitions ->
            val topic = Topic("topic", config)
            val record = Record("topic", 1000, 3)

            topic.addRecordToPartition(record, 4)

            verify(partitions.constructed()[3]).addRecord(record)
        }
    }

    @Test
    fun `addRecordToPartition will not add record to the wrong partition`() {
        mockConstruction(
            Partition::class.java
        ) { mock, settings ->
            whenever(mock.partitionId).thenReturn(settings.count)
        }.use { partitions ->
            val topic = Topic("topic", config)
            val record = Record("topic", 1000, 3)

            topic.addRecordToPartition(record, 4)

            verify(partitions.constructed()[2], never()).addRecord(record)
        }
    }

    @Test
    fun `addRecordToPartition will throw an exception if the partition is unknown`() {
        mockConstruction(
            Partition::class.java
        ) { mock, settings ->
            whenever(mock.partitionId).thenReturn(settings.count)
        }.use {
            val topic = Topic("topic", config)
            val record = Record("topic", 1000, 3)

            assertThrows<IllegalStateException> {
                topic.addRecordToPartition(record, 50)
            }
        }
    }

    @Test
    fun `addRecordToPartition will wake up any group`() {
        val topic = Topic("topic", config)
        mockConstruction(ConsumerGroup::class.java).use { group ->
            val subscriptionConfig = SubscriptionConfiguration(10, 100L)
            val consumer = mock<Consumer> {
                on { groupName } doReturn "group"
            }
            topic.subscribe(consumer, subscriptionConfig)
            val record = Record("topic", 1004, 3)

            topic.addRecordToPartition(record, 1)

            verify(group.constructed().first()).wakeUp()
        }
    }

    @Test
    fun `handleAllRecords send the request to the partitions`() {
        mockConstruction(Partition::class.java).use { partitions ->
            val topic = Topic("topic", config)

            topic.handleAllRecords(mock())

            verify(partitions.constructed()[1]).handleAllRecords(any())
        }
    }
}
