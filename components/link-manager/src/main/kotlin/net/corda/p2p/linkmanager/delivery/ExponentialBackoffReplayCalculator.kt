package net.corda.p2p.linkmanager.delivery

import java.time.Duration

internal class ExponentialBackoffReplayCalculator(
    limitTotalReplays: Boolean,
    private val config: ReplayScheduler.ReplaySchedulerConfig.ExponentialBackoffReplaySchedulerConfig
) : ReplayCalculator(limitTotalReplays, config) {

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
}