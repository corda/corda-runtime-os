package net.corda.messaging.emulation.subscription.eventlog

import io.mockk.mockk
import io.mockk.verify
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.PartitionAssignmentListener
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PartitionerTest {

    @Test
    fun `invoke return the correct value`() {
        val testObject = Partitioner(null)
        val record = Record(
            "topic",
            -332,
            mockk()
        )

        val partition = testObject.invoke(record)

        assertThat(partition).isEqualTo(2)
    }

    @Test
    fun `invoke return 9 for 9`() {
        val testObject = Partitioner(null)
        val record = Record(
            "topic",
            9,
            mockk()
        )

        val partition = testObject.invoke(record)

        assertThat(partition).isEqualTo(9)
    }

    @Test
    fun `invoke return 0 for 10`() {
        val testObject = Partitioner(null)
        val record = Record(
            "topic",
            10,
            mockk()
        )

        val partition = testObject.invoke(record)

        assertThat(partition).isEqualTo(0)
    }

    @Test
    fun `invoke sends new partition when the partition is new`() {
        val listener = mockk<PartitionAssignmentListener>(relaxed = true)
        val testObject = Partitioner(listener)
        val record = Record(
            "topic",
            1,
            mockk()
        )

        testObject.invoke(record)

        verify {
            listener.onPartitionsAssigned(listOf("topic" to 1))
        }
    }

    @Test
    fun `invoke sends new partition only once`() {
        val listener = mockk<PartitionAssignmentListener>(relaxed = true)
        val testObject = Partitioner(listener)
        val record1 = Record(
            "topic",
            1,
            mockk()
        )
        val record2 = Record(
            "topic",
            11,
            mockk()
        )
        val record3 = Record(
            "topic",
            111,
            mockk()
        )

        testObject.invoke(record1)
        testObject.invoke(record2)
        testObject.invoke(record3)

        verify(exactly = 1) {
            listener.onPartitionsAssigned(any())
        }
    }

    @Test
    fun `invoke sends two partitions for two different tropics`() {
        val listener = mockk<PartitionAssignmentListener>(relaxed = true)
        val testObject = Partitioner(listener)
        val record1 = Record(
            "topic1",
            1,
            mockk()
        )
        val record2 = Record(
            "topic2",
            11,
            mockk()
        )

        testObject.invoke(record1)
        testObject.invoke(record2)

        verify {
            listener.onPartitionsAssigned(listOf("topic1" to 1))
            listener.onPartitionsAssigned(listOf("topic2" to 1))
        }
    }
}
