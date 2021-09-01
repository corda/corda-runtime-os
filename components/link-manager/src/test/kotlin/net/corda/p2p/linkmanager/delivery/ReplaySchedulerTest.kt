package net.corda.p2p.linkmanager.delivery

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*
import java.util.concurrent.CountDownLatch

class ReplaySchedulerTest {

    class TrackReplayedMessages(numUniqueMessages: Int) {
        private val latch = CountDownLatch(numUniqueMessages)
        private val replayedMessageIds = mutableSetOf<String>()

        fun replayMessage(messageId: String) {
            if (replayedMessageIds.contains(messageId)) return
            replayedMessageIds += messageId
            latch.countDown()
        }

        fun await() {
            latch.await()
        }
    }

    @Test
    fun `The ReplayScheduler will not replay before start`() {
        val replayPeriod = 5L
        val replayManager = ReplayScheduler(replayPeriod, { _: Any -> } ) { 0 }
        assertThrows<ReplayScheduler.TaskAddedForReplayWhenNotStartedException> {
            replayManager.addForReplay(0,"", Any())
        }
    }

    @Test
    fun `The ReplayScheduler replays added messages`() {
        val replayPeriod = 5L
        val messages = 9

        val tracker = TrackReplayedMessages(messages)

        val replayManager = ReplayScheduler(replayPeriod, tracker::replayMessage) { 0 }
        replayManager.start()
        for (i in 0 until messages) {
            val messageId = UUID.randomUUID().toString()
            replayManager.addForReplay(
                0,
                messageId,
                messageId
            )
        }

        tracker.await()
        replayManager.stop()
    }

    class TwoPhaseTrackReplayedMessages(private val firstNumUniqueMessages: Int, private val secondNumUniqueMessages: Int) {
        val firstPhaseWaitLatch = CountDownLatch(1)
        val secondPhaseStartLatch = CountDownLatch(1)
        val secondPhaseWaitLatch = CountDownLatch(1)
        val replayedMessageIds = mutableMapOf<String, Int>()

        private var secondPhase = false

        fun replayMessage(messageId: String) {
            val replays = replayedMessageIds[messageId]
            if (replays == null) {
                replayedMessageIds[messageId] = 1
            } else {
                replayedMessageIds[messageId] = replays + 1
            }
            if (replayedMessageIds.keys.size == firstNumUniqueMessages) {
                replayedMessageIds.clear()
                firstPhaseWaitLatch.countDown()
                secondPhaseStartLatch.await()
                secondPhase = true
            }
            if (secondPhase && replayedMessageIds.keys.size == secondNumUniqueMessages) {
                secondPhaseWaitLatch.countDown()
            }
        }
    }

    @Test
    fun `The ReplayScheduler doesn't replay removed messages`() {
        val replayPeriod = 5L
        val messages = 8

        val tracker = TwoPhaseTrackReplayedMessages(messages, 4)

        val replayManager = ReplayScheduler(replayPeriod, tracker::replayMessage) { 0 }
        replayManager.start()

        val messageIdsToRemove = mutableListOf<String>()
        val messageIdsToNotRemove = mutableListOf<String>()
        for (i in 0 until messages) {
            val messageId = UUID.randomUUID().toString()
            if (i % 2 == 0) messageIdsToRemove.add(messageId)
            else messageIdsToNotRemove.add(messageId)
            replayManager.addForReplay(
                0,
                messageId,
                messageId
            )
        }

        tracker.firstPhaseWaitLatch.await()
        //Acknowledge all even messages
        for (id in messageIdsToRemove) {
            replayManager.removeFromReplay(id)
        }
        tracker.secondPhaseStartLatch.countDown()

        tracker.secondPhaseWaitLatch.await()
        Assertions.assertArrayEquals(messageIdsToNotRemove.toTypedArray(), tracker.replayedMessageIds.keys.toTypedArray())

        replayManager.stop()
    }
}