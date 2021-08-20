package net.corda.p2p.linkmanager.delivery

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.*
import java.util.concurrent.CountDownLatch

class ReplaySchedulerTest {

    /**
     * The class produces timestamps which cause some messages to replay once. Subsequent calls to [getTimeStamp] return 0
     * which causes no messages to replay.
     * The idea being this will cause the delivery tracker to replay some messages once in [ReplayScheduler.replayMessages].
     * [awaitSecondReplay] waits until getTimestamp as been called twice inside replayMessages (after which some messages have
     * replayed once).
     */
    class ReplayOnceTimeStamper(private val timeStamp: Long) {
        private val secondReplayLatch = CountDownLatch(1)
        private var getTimeStampCalls = 0

        fun getTimeStamp(): Long {
            if (getTimeStampCalls == 1) secondReplayLatch.countDown()
            getTimeStampCalls++
            return if (getTimeStampCalls == 1) {
                timeStamp
            } else {
                0
            }
        }

        fun awaitSecondReplay() {
            secondReplayLatch.await()
        }
    }

    @Test
    fun `The ReplayScheduler replays added messages`() {
        val replayedMessages = mutableListOf<DeliveryTracker.PositionInTopic>()
        val replayMessage = fun(position: DeliveryTracker.PositionInTopic) {
            replayedMessages.add(position)
        }
        val replayPeriod = 5L
        val messages = 9
        val timeStamper = ReplayOnceTimeStamper(messages.toLong())

        val replayManager = ReplayScheduler(replayPeriod, replayMessage, timeStamper::getTimeStamp)
        for (i in 0 until messages) {
            val messageId = UUID.randomUUID().toString()
            replayManager.addForReplay(
                i.toLong(),
                messageId,
                DeliveryTracker.PositionInTopic(1, i.toLong())
            )
        }
        replayManager.start()
        timeStamper.awaitSecondReplay()

        Assertions.assertEquals(messages - replayPeriod + 1, replayedMessages.size.toLong())
        for (i in 0 until messages - replayPeriod) {
            Assertions.assertEquals(
                DeliveryTracker.PositionInTopic(1, i),
                replayedMessages[i.toInt()]
            )
        }
        replayManager.stop()
    }

    @Test
    fun `The ReplayScheduler doesn't replay removed messages`() {
        val replayedMessages = mutableListOf<DeliveryTracker.PositionInTopic>()
        val replayMessage = fun(position: DeliveryTracker.PositionInTopic) {
            replayedMessages.add(position)
        }
        val replayPeriod = 5L
        val messages = 9

        val timeStamper = ReplayOnceTimeStamper(messages.toLong())

        val replayManager = ReplayScheduler(replayPeriod, replayMessage, timeStamper::getTimeStamp)
        val messageIds = mutableListOf<String>()
        for (i in 0 until messages) {
            val messageId = UUID.randomUUID().toString()
            messageIds.add(messageId)
            replayManager.addForReplay(
                i.toLong(),
                messageId,
                DeliveryTracker.PositionInTopic(1, i.toLong())
            )
        }

        //Acknowledge all even messages
        for (i in 0 until messages) {
            if (i % 2 == 0) replayManager.removeFromReplay(messageIds[i])
        }
        replayManager.start()
        timeStamper.awaitSecondReplay()

        Assertions.assertEquals((messages - replayPeriod + 1) / 2L, replayedMessages.size.toLong())
        for (i in 0 until (messages - replayPeriod) / 2L) {
            Assertions.assertEquals(
                DeliveryTracker.PositionInTopic(1, 2 * i + 1),
                replayedMessages[i.toInt()]
            )
        }
        replayManager.stop()
    }
}