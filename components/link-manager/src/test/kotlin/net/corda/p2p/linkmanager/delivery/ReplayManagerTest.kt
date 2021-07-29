package net.corda.p2p.linkmanager.delivery

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.*
import java.util.concurrent.CountDownLatch

class ReplayManagerTest {

    /**
     * [getTimeStamp] returns an incrementing timestamp until [replaysStarted] is called.
     * After [replaysStarted] is called the timestamp will increment once more before always returning
     * zero. The idea being this will cause the delivery tracker to replay some messages once in [ReplayManager.replayMessages].
     * [awaitSecondReplay] waits until getTimestamp as been called twice inside replayMessages (after which some messages have
     * replayed once).
     */
    class ReplayOnceTimeStamper {
        private var replaysStarted = false
        private val secondReplayLatch = CountDownLatch(1)
        private var timeStamp = 0L

        fun getTimeStamp(): Long {
            return if (!replaysStarted) {
                timeStamp++
            } else {
                if (timeStamp == 0L) secondReplayLatch.countDown()
                val currentTimeStamp = timeStamp
                timeStamp = 0
                currentTimeStamp
            }
        }

        fun replaysStarted() {
            replaysStarted = true
        }

        fun awaitSecondReplay() {
            secondReplayLatch.await()
        }
    }

    @Test
    fun `The DeliveryTracker replays messages which haven't been acknowledged before timed out`() {
        val replayedMessages = mutableListOf<DeliveryTracker.HighWaterMarkTracker.PositionInTopic>()
        val replayMessage = fun(position: DeliveryTracker.HighWaterMarkTracker.PositionInTopic) {
            replayedMessages.add(position)
        }
        val replayPeriod = 5L
        val messages = 9
        val timeStamper = ReplayOnceTimeStamper()

        val replayManager = ReplayManager(replayPeriod, replayMessage, timeStamper::getTimeStamp)
        for (i in 0 until messages) {
            val messageId = UUID.randomUUID().toString()
            replayManager.addForReplay(messageId,
                DeliveryTracker.HighWaterMarkTracker.PositionInTopic(1, i.toLong())
            )
        }
        timeStamper.replaysStarted()
        replayManager.start()
        timeStamper.awaitSecondReplay()

        Assertions.assertEquals(messages - replayPeriod + 1, replayedMessages.size.toLong())
        for (i in 0 until messages - replayPeriod) {
            Assertions.assertEquals(
                DeliveryTracker.HighWaterMarkTracker.PositionInTopic(1, i),
                replayedMessages[i.toInt()]
            )
        }
        replayManager.stop()
    }

    @Test
    fun `The DeliveryTracker doesn't replay acknowledged messages`() {
        val replayedMessages = mutableListOf<DeliveryTracker.HighWaterMarkTracker.PositionInTopic>()
        val replayMessage = fun(position: DeliveryTracker.HighWaterMarkTracker.PositionInTopic) {
            replayedMessages.add(position)
        }
        val replayPeriod = 5L
        val messages = 9

        val timeStamper = ReplayOnceTimeStamper()

        val replayManager = ReplayManager(replayPeriod, replayMessage, timeStamper::getTimeStamp)
        val messageIds = mutableListOf<String>()
        for (i in 0 until messages) {
            val messageId = UUID.randomUUID().toString()
            messageIds.add(messageId)
            replayManager.addForReplay(messageId,
                DeliveryTracker.HighWaterMarkTracker.PositionInTopic(1, i.toLong())
            )
        }

        //Acknowledge all even messages
        for (i in 0 until messages) {
            if (i % 2 == 0) replayManager.removeFromReplay(messageIds[i])
        }
        timeStamper.replaysStarted()
        replayManager.start()
        timeStamper.awaitSecondReplay()

        Assertions.assertEquals((messages - replayPeriod + 1) / 2L, replayedMessages.size.toLong())
        for (i in 0 until (messages - replayPeriod) / 2L) {
            Assertions.assertEquals(
                DeliveryTracker.HighWaterMarkTracker.PositionInTopic(1, 2 * i + 1),
                replayedMessages[i.toInt()]
            )
        }
        replayManager.stop()
    }
}