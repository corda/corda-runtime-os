package net.corda.messaging.db.subscription

import net.corda.messaging.db.persistence.FetchWindow
import net.corda.messaging.db.sync.OffsetTrackersManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class FetchWindowCalculatorTest {

    private val topic = "test.topic"

    private val offsetTrackersManager = mock(OffsetTrackersManager::class.java)
    private val fetchWindowCalculator = FetchWindowCalculator(offsetTrackersManager)

    @Test
    fun `fetch windows are calculated properly when records are imbalanced in partitions`() {
        val committedOffsetsPerPartition = mapOf(
            1 to 5L,
            2 to 4L,
            3 to 8L
        )
        val maxVisibleOffsetsPerPartition = mapOf(
            1 to 25L,
            2 to 6L,
            3 to 12L
        )
        maxVisibleOffsetsPerPartition.forEach { (partition, offset) ->
            `when`(offsetTrackersManager.maxVisibleOffset(topic, partition)).thenReturn(offset)
        }

        val windows = fetchWindowCalculator.calculateWindows(topic, 10, committedOffsetsPerPartition)
        assertThat(windows).containsAll(
            listOf(
                FetchWindow(1, 6, 25, 8),
                FetchWindow(2, 5, 6, 1),
                FetchWindow(3, 9, 12, 1)
            )
        )
    }

    @Test
    fun `fetch windows are calculated properly when records are balanced in partitions`() {
        val committedOffsetsPerPartition = mapOf(
            1 to 5L,
            2 to 5L,
            3 to 5L
        )
        val maxVisibleOffsetsPerPartition = mapOf(
            1 to 25L,
            2 to 25L,
            3 to 25L
        )
        maxVisibleOffsetsPerPartition.forEach { (partition, offset) ->
            `when`(offsetTrackersManager.maxVisibleOffset(topic, partition)).thenReturn(offset)
        }

        val windows = fetchWindowCalculator.calculateWindows(topic, 15, committedOffsetsPerPartition)
        assertThat(windows).containsAll(
            listOf(
                FetchWindow(1, 6, 25, 5),
                FetchWindow(2, 6, 25, 5),
                FetchWindow(3, 6, 25, 5)
            )
        )
    }

    @Test
    fun `partitions that do not have any records are not included in the returned windows`() {
        val committedOffsetsPerPartition = mapOf(
            1 to 5L,
            2 to 5L,
            3 to 5L
        )
        val maxVisibleOffsetsPerPartition = mapOf(
            1 to 8L,
            2 to 5L,
            3 to 5L
        )
        maxVisibleOffsetsPerPartition.forEach {
            (partition, offset) ->
            `when`(offsetTrackersManager.maxVisibleOffset(topic, partition)).thenReturn(offset)
        }

        val windows = fetchWindowCalculator.calculateWindows(topic, 10, committedOffsetsPerPartition)
        assertThat(windows).containsAll(
            listOf(
                FetchWindow(1, 6, 8, 10)
            )
        )
    }

    @Test
    fun `if the total number of records available is zero, an empty list of windows is returned`() {
        val committedOffsetsPerPartition = mapOf(
            1 to 5L,
            2 to 5L,
            3 to 5L
        )
        val maxVisibleOffsetsPerPartition = mapOf(
            1 to 5L,
            2 to 5L,
            3 to 5L
        )
        maxVisibleOffsetsPerPartition.forEach { (partition, offset) ->
            `when`(offsetTrackersManager.maxVisibleOffset(topic, partition)).thenReturn(offset)
        }

        val windows = fetchWindowCalculator.calculateWindows(topic, 15, committedOffsetsPerPartition)
        assertThat(windows).isEmpty()
    }
}
