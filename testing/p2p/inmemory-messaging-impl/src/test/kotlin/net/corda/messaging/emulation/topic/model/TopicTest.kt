package net.corda.messaging.emulation.topic.model

import net.corda.messaging.api.records.Record
import net.corda.messaging.emulation.properties.SubscriptionConfiguration
import net.corda.messaging.emulation.properties.TopicConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration

class TopicTest {
    private val config = TopicConfiguration(5, 10)

    @Test
    fun `createConsumption will subscribe to the correct group`() {
        val topic = Topic("topic", config)
        mockConstruction(ConsumerGroup::class.java).use { group ->
            val subscriptionConfig = SubscriptionConfiguration(10, Duration.ofSeconds(1))
            val consumer = mock<Consumer> {
                on { groupName } doReturn "group"
            }

            topic.createConsumption(consumer, subscriptionConfig)

            verify(group.constructed().first()).createConsumption(consumer)
        }
    }

    @Test
    fun `createConsumption will create a group with the correct arguments`() {
        val topic = Topic("topic", config)
        mockConstruction(ConsumerGroup::class.java) { _, context ->
            assertThat(context.arguments()[0] as Collection<Any?>).hasSize(5)
        }.use {
            val subscriptionConfig = SubscriptionConfiguration(10, Duration.ofSeconds(1))
            val consumer = mock<Consumer> {
                on { groupName } doReturn "group"
            }

            topic.createConsumption(consumer, subscriptionConfig)
        }
    }

    @Test
    fun `unsubscribe will unsubscribe to the correct group`() {
        val topic = Topic("topic", config)
        mockConstruction(ConsumerGroup::class.java).use { group ->
            val subscriptionConfig = SubscriptionConfiguration(10, Duration.ofSeconds(1))
            val consumer = mock<Consumer> {
                on { groupName } doReturn "group"
            }
            topic.createConsumption(consumer, subscriptionConfig)

            topic.unsubscribe(consumer)

            verify(group.constructed().first()).stopConsuming(consumer)
        }
    }

    @Test
    fun `unsubscribe will not unsubscribe unknown consumer`() {
        val topic = Topic("topic", config)
        mockConstruction(ConsumerGroup::class.java).use { group ->
            val subscriptionConfig = SubscriptionConfiguration(10, Duration.ofSeconds(1))
            val consumer1 = mock<Consumer> {
                on { groupName } doReturn "group"
            }
            val consumer2 = mock<Consumer> {
                on { groupName } doReturn "group2"
            }
            topic.createConsumption(consumer1, subscriptionConfig)

            topic.unsubscribe(consumer2)

            verify(group.constructed().first(), never()).stopConsuming(any())
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
    fun `getPartition will find the correct partition for record`() {
        val topic = Topic("topic", config)
        val record = Record("topic", 1005, 3)

        val partition = topic.getPartition(record)

        assertThat(partition.partitionId).isEqualTo(1)
    }

    @Test
    fun `getPartition will find the correct partition for partition ID`() {
        val topic = Topic("topic", config)

        val partition = topic.getPartition(2)

        assertThat(partition.partitionId).isEqualTo(2)
    }

    @Test
    fun `getPartition will throw an exception for invalid ID`() {
        val topic = Topic("topic", config)

        assertThrows<IllegalStateException> {
            topic.getPartition(20)
        }
    }

    @Test
    fun `handleAllRecords send the request to the partitions`() {
        mockConstruction(
            Partition::class.java
        ) { mock, _ ->
            whenever(mock.partitionId).thenReturn(10)
            whenever(mock.latestOffset()).thenReturn(43L)
        }.use {
            val topic = Topic("topic", config)

            val offsets = topic.getLatestOffsets()

            assertThat(offsets).isEqualTo(mapOf(10 to 42L))
        }
    }

    @Test
    fun `wakeUpConsumers wakes up all the consumers group`() {
        val topic = Topic("topic", config)
        mockConstruction(ConsumerGroup::class.java).use { group ->
            val subscriptionConfig = SubscriptionConfiguration(10, Duration.ofSeconds(1))
            val consumer = mock<Consumer> {
                on { groupName } doReturn "group"
            }
            topic.createConsumption(consumer, subscriptionConfig)

            topic.wakeUpConsumers()

            verify(group.constructed().first()).wakeUp()
        }
    }

    @Test
    fun `assignPartition will assign the partitions`() {
        val topic = Topic("topic", config)
        mockConstruction(ConsumerGroup::class.java).use { group ->
            val partitions = argumentCaptor<Collection<Partition>>()
            val subscriptionConfig = SubscriptionConfiguration(10, Duration.ofSeconds(1))
            val consumer = mock<Consumer> {
                on { groupName } doReturn "group"
            }
            topic.createConsumption(consumer, subscriptionConfig)
            doNothing().whenever(group.constructed().first()).assignPartition(eq(consumer), partitions.capture())

            topic.assignPartition(consumer, listOf(1, 2, 3))

            assertThat(partitions.firstValue.map { it.partitionId }).containsExactlyInAnyOrder(1, 2, 3)
        }
    }

    @Test
    fun `assignPartition will throw an exception for consumer with an unknown group`() {
        val topic = Topic("topic", config)
        val consumer = mock<Consumer> {
            on { groupName } doReturn "group"
        }

        assertThrows<java.lang.IllegalStateException> {
            topic.assignPartition(consumer, listOf(1, 2, 3))
        }
    }

    @Test
    fun `unAssignPartition will un assign the partitions`() {
        val topic = Topic("topic", config)
        mockConstruction(ConsumerGroup::class.java).use { group ->
            val partitions = argumentCaptor<Collection<Partition>>()
            val subscriptionConfig = SubscriptionConfiguration(10, Duration.ofSeconds(1))
            val consumer = mock<Consumer> {
                on { groupName } doReturn "group"
            }
            topic.createConsumption(consumer, subscriptionConfig)
            doNothing().whenever(group.constructed().first()).unAssignPartition(eq(consumer), partitions.capture())

            topic.unAssignPartition(consumer, listOf(1, 2, 3))

            assertThat(partitions.firstValue.map { it.partitionId }).containsExactlyInAnyOrder(1, 2, 3)
        }
    }

    @Test
    fun `unAssignPartition will throw an exception for consumer with an unknown group`() {
        val topic = Topic("topic", config)
        val consumer = mock<Consumer> {
            on { groupName } doReturn "group"
        }

        assertThrows<java.lang.IllegalStateException> {
            topic.unAssignPartition(consumer, listOf(1, 2, 3))
        }
    }
}
