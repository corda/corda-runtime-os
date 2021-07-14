package net.corda.messaging.db.partition

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class PartitionAssignorTest {

    private val numberOfPartitions = 10
    private val partitions = (1..numberOfPartitions)

    private val partitionAssignor = PartitionAssignor()

    @Test
    fun `records with different keys are assigned to different partitions`() {
        val partition1 = partitionAssignor.assign("key-1".toByteArray(), numberOfPartitions)
        val partition2 = partitionAssignor.assign("key-2".toByteArray(), numberOfPartitions)

        assertThat(partition1).isNotEqualTo(partition2)
        assertThat(partition1).isIn(partitions)
        assertThat(partition2).isIn(partitions)
    }

    @Test
    fun `records with the same key are assigned to the same partition`() {
        val partition1 = partitionAssignor.assign("key-1".toByteArray(), numberOfPartitions)
        val partition2 = partitionAssignor.assign("key-1".toByteArray(), numberOfPartitions)

        assertThat(partition1).isEqualTo(partition2)
        assertThat(partition1).isIn(partitions)
    }

    @Test
    fun `records with random keys fall within the expected range`() {
        for (i in 1..100) {
            val randomKey = UUID.randomUUID().toString().toByteArray()
            val partition = partitionAssignor.assign(randomKey, numberOfPartitions)

            assertThat(partition).isIn(partitions)
        }
    }

    @Test
    fun `throws an error if invoked with negative number of partitions`() {
        val randomKey = UUID.randomUUID().toString().toByteArray()

        assertThatThrownBy { partitionAssignor.assign(randomKey, -1) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

}