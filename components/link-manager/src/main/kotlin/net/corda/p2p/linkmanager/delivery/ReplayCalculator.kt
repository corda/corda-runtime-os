package net.corda.p2p.linkmanager.delivery

import java.time.Duration

internal interface ReplayCalculator {
    fun calculateReplayInterval(lastDelay: Duration = Duration.ZERO): Duration
    fun shouldReplayMessage(currentNumberOfReplayingMessages: Int): Boolean
    fun extraMessagesToReplay(maxNumberOfReplayingMessagesBefore: Int): Int
}

internal interface ReplayCalculatorFactory {
    fun fromConfig(config: ReplayScheduler.ReplaySchedulerConfig): ReplayCalculator
}

internal class ExponentialBackoffWithMaxReplayCalculatorFactory: ReplayCalculatorFactory {

    override fun fromConfig(config: ReplayScheduler.ReplaySchedulerConfig): ReplayCalculator {
        return object : ReplayCalculator {
            override fun calculateReplayInterval(lastDelay: Duration): Duration {
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

            override fun shouldReplayMessage(currentNumberOfReplayingMessages: Int): Boolean {
                return currentNumberOfReplayingMessages < config.maxReplayingMessages
            }

            override fun extraMessagesToReplay(maxNumberOfReplayingMessagesBefore: Int)
                = config.maxReplayingMessages - maxNumberOfReplayingMessagesBefore
        }
    }
}

internal class ExponentialBackoffReplayCalculator: ReplayCalculatorFactory {
    override fun fromConfig(config: ReplayScheduler.ReplaySchedulerConfig): ReplayCalculator {
        return object : ReplayCalculator {
            override fun calculateReplayInterval(lastDelay: Duration): Duration {
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

            override fun shouldReplayMessage(currentNumberOfReplayingMessages: Int): Boolean = true

            override fun extraMessagesToReplay(maxNumberOfReplayingMessagesBefore: Int): Int = 0
        }
    }
}