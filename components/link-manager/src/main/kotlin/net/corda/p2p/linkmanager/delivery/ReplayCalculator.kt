package net.corda.p2p.linkmanager.delivery

import java.time.Duration

internal abstract class ReplayCalculator(
    private val limitTotalReplays: Boolean,
    private val config: ReplayScheduler.ReplaySchedulerConfig
) {
    abstract fun calculateReplayInterval(lastDelay: Duration = Duration.ZERO): Duration

    fun shouldReplayMessage(currentNumberOfReplayingMessages: Int): Boolean {
        return if (limitTotalReplays) {
            currentNumberOfReplayingMessages < config.maxReplayingMessages
        } else {
            true
        }
    }

     fun extraMessagesToReplay(maxNumberOfReplayingMessagesBefore: Int): Int {
        return if (limitTotalReplays) {
            config.maxReplayingMessages - maxNumberOfReplayingMessagesBefore
        } else {
            0
        }
    }
}