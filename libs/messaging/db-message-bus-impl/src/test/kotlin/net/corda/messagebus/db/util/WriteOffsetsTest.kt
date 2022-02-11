package net.corda.messagebus.db.util

import net.corda.messagebus.api.CordaTopicPartition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class WriteOffsetsTest {

    private val topic1 = "topic1"
    private val topic2 = "topic2"

    @Test
    fun `returns the correct next offset`() {
        val initialState = mutableMapOf(
            CordaTopicPartition(topic1, 0) to 5L,
            CordaTopicPartition(topic1, 1) to 0L,
            CordaTopicPartition(topic1, 2) to 2L,
            CordaTopicPartition(topic2, 0) to 0L,
        )

        val writeOffsets = WriteOffsets(initialState)

        assertThat(writeOffsets.getNextOffsetFor(CordaTopicPartition(topic1, 0))).isEqualTo(5L)
        assertThat(writeOffsets.getNextOffsetFor(CordaTopicPartition(topic1, 0))).isEqualTo(6L)
        assertThat(writeOffsets.getNextOffsetFor(CordaTopicPartition(topic1, 0))).isEqualTo(7L)

        assertThat(writeOffsets.getNextOffsetFor(CordaTopicPartition(topic1, 1))).isEqualTo(0L)
        assertThat(writeOffsets.getNextOffsetFor(CordaTopicPartition(topic1, 1))).isEqualTo(1L)

        assertThat(writeOffsets.getNextOffsetFor(CordaTopicPartition(topic1, 2))).isEqualTo(2L)
        assertThat(writeOffsets.getNextOffsetFor(CordaTopicPartition(topic1, 2))).isEqualTo(3L)

        assertThat(writeOffsets.getNextOffsetFor(CordaTopicPartition(topic2, 0))).isEqualTo(0L)
        assertThat(writeOffsets.getNextOffsetFor(CordaTopicPartition(topic2, 0))).isEqualTo(1L)
    }
}
