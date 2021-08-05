package net.corda.messaging.emulation.subscription.eventlog

import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.PartitionAssignmentListener
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class PartitionerTest {

    @Test
    fun `invoke return the correct value`() {
        val testObject = Partitioner(null, 10)
        val record = Record(
            "topic",
            -332,
            mock()
        )

        val partition = testObject.invoke(record)

        assertThat(partition).isEqualTo(2)
    }

    @Test
    fun `invoke return 9 for 9`() {
        val testObject = Partitioner(null, 10)
        val record = Record(
            "topic",
            9,
            mock()
        )

        val partition = testObject.invoke(record)

        assertThat(partition).isEqualTo(9)
    }

    @Test
    fun `invoke return 0 for 10`() {
        val testObject = Partitioner(null, 10)
        val record = Record(
            "topic",
            10,
            mock()
        )

        val partition = testObject.invoke(record)

        assertThat(partition).isEqualTo(0)
    }

    @Test
    fun `invoke sends new partition when the partition is new`() {
        val listener = mock<PartitionAssignmentListener>()
        val testObject = Partitioner(listener, 10)
        val record = Record(
            "topic",
            1,
            mock()
        )

        testObject.invoke(record)

        verify(listener).onPartitionsAssigned(listOf("topic" to 1))
    }

    @Test
    fun `invoke sends new partition only once`() {
        val listener = mock<PartitionAssignmentListener>()
        val testObject = Partitioner(listener, 10)
        val record1 = Record(
            "topic",
            1,
            mock()
        )
        val record2 = Record(
            "topic",
            11,
            mock()
        )
        val record3 = Record(
            "topic",
            111,
            mock()
        )

        testObject.invoke(record1)
        testObject.invoke(record2)
        testObject.invoke(record3)

        verify(times(1)) {
            listener.onPartitionsAssigned(any())
        }
    }

    @Test
    fun `invoke sends two partitions for two different tropics`() {
        val listener = mock<PartitionAssignmentListener>()
        val testObject = Partitioner(listener, 10)
        val record1 = Record(
            "topic1",
            1,
            mock()
        )
        val record2 = Record(
            "topic2",
            11,
            mock()
        )

        testObject.invoke(record1)
        testObject.invoke(record2)

        verify(listener).onPartitionsAssigned(listOf("topic1" to 1))
        verify(listener).onPartitionsAssigned(listOf("topic2" to 1))
    }
}
