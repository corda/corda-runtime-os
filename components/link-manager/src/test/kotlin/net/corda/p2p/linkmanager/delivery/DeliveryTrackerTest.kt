package net.corda.p2p.linkmanager.delivery

import net.corda.p2p.linkmanager.delivery.DeliveryTracker.HighWaterMarkTracker
import net.corda.p2p.linkmanager.delivery.DeliveryTracker.HighWaterMarkTracker.PositionInTopic
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.*

class DeliveryTrackerTest {

    @Test
    fun `HighWaterMarkTracker generates the correct offset when start and end markers are added contiguously`() {
        val tracker = HighWaterMarkTracker({ _: String, _: PositionInTopic -> }, { })
        tracker.addPartition(1, -1)

        val numMarkers = 10

        for (i in 0 until numMarkers) {
            val messageId = UUID.randomUUID().toString()
            val startMarkerOffset = 2L * i
            val endMarkerOffset = 2L * i + 1
            tracker.processSentMarker(PositionInTopic(1, startMarkerOffset), messageId, PositionInTopic(1, 0))
            assertEquals(endMarkerOffset, tracker.processReceivedMarker(PositionInTopic(1, endMarkerOffset), messageId))
        }
    }

    @Test
    fun `HighWaterMarkTracker generates the correct offset when start then end markers are added in ascending order`() {
        val tracker = HighWaterMarkTracker({ _: String, _: PositionInTopic -> }, { })
        tracker.addPartition(1, -1)
        val numRepeats = 2
        val numMarkers = 10
        val messageIds = mutableListOf<String>()
        var offset = 0L
        for (repeat in 0 until numRepeats) {
            for (i in 0 until numMarkers) {
                val messageId = UUID.randomUUID().toString()
                messageIds.add(messageId)
                tracker.processSentMarker(PositionInTopic(1, offset), messageId, PositionInTopic(1, 0))
                offset++
            }

            for (i in 0 until numMarkers) {
                val messageId = messageIds[i + repeat * numMarkers]
                val offsetToCommit = tracker.processReceivedMarker(PositionInTopic(1, offset), messageId)
                if (i != numMarkers - 1) {
                    assertEquals(i.toLong() + 2 * (repeat * numMarkers), offsetToCommit)
                } else {
                    assertEquals( numMarkers + i.toLong() + 2 * (repeat * numMarkers), offsetToCommit)
                }
                offset++
            }
        }
    }

    @Test
    fun `HighWaterMarkTracker generates the correct offset when start markers are added in ascending order then end markers are added in descending order`() {
        val tracker = HighWaterMarkTracker({ _: String, _: PositionInTopic -> }, { })
        tracker.addPartition(1, -1)
        val numRepeats = 2
        val numMarkers = 10
        val messageIds = mutableListOf<String>()
        var offset = 0L
        for (repeat in 0 until numRepeats) {
            for (i in 0 until numMarkers) {
                val messageId = UUID.randomUUID().toString()
                messageIds.add(messageId)
                tracker.processSentMarker(PositionInTopic(1, offset), messageId, PositionInTopic(1, 0))
                offset++
            }

            for (i in (0 until numMarkers).reversed()) {
                val messageId = messageIds[i + repeat * numMarkers]
                val offsetToCommit = tracker.processReceivedMarker(PositionInTopic(1, offset), messageId)
                if (i != 0) {
                    assertNull(offsetToCommit)
                } else {
                    assertEquals( 2L * numMarkers - 1 + 2 * (repeat * numMarkers), offsetToCommit)
                }
                offset++
            }
        }
    }

    @Test
    fun `HighWaterMarkTracker advances the high water mark if we never processed a sent marker`() {
        val tracker = HighWaterMarkTracker({ _: String, _: PositionInTopic -> }, { })
        tracker.addPartition(1, -1)
        val numMarkers = 10
        for (i in 0 until numMarkers) {
            assertEquals(i.toLong(), tracker.processReceivedMarker(PositionInTopic(1, i.toLong()), ""))
        }
    }

    @Test
    fun `HighWaterMarkTracker doesn't advance the highwater mark if it is not subscribed to the topic`() {
        val tracker = HighWaterMarkTracker({ _: String, _: PositionInTopic -> }, { })
        tracker.addPartition(1, -1)
        assertNull(tracker.processReceivedMarker(PositionInTopic(2, 0), ""))
    }
}