package net.corda.p2p.linkmanager.tracker

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class DeliveryTrackerOffsetProviderTest {
    private val partitionsIndices = setOf(4, 6, 1)
    private val stateOne = mock<PartitionState> {
        on { readRecordsFromOffset } doReturn 200
    }
    private val stateTwo = mock<PartitionState> {
        on { readRecordsFromOffset } doReturn 310
    }
    private val partitionStates = mock<PartitionsStates> {
        on { loadPartitions(partitionsIndices) } doReturn
            mapOf(
                5 to stateOne,
                10 to stateTwo,
            )
    }

    private val provider = DeliveryTrackerOffsetProvider(partitionStates)

    @Test
    fun `getStartingOffsets return the correct offsets`() {
        val offsets = provider.getStartingOffsets(
            partitionsIndices.map { "topic" to it }.toSet(),
        )

        assertThat(offsets)
            .hasSize(2)
            .containsEntry(("topic" to 5), 200L)
            .containsEntry(("topic" to 10), 310L)
    }
}
