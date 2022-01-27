package net.corda.p2p.linkmanager.delivery

import java.time.Duration

internal class ReplayCalculator(private val limitTotalReplays: Boolean, private val config: ReplayScheduler.ReplaySchedulerConfig) {
    fun calculateReplayInterval(lastDelay: Duration = Duration.ZERO): Duration {
        val delay = lastDelay.multipliedBy(2)
        return when {
            delay > config.cutOff -> {
                config.cutOff
            }
            delay < config.baseReplayPeriod -> {
                config.baseReplayPeriod
            }
            else -> {
                delay
            }
        }
    }

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