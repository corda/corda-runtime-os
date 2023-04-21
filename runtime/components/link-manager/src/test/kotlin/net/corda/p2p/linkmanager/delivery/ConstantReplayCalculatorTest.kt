package net.corda.p2p.linkmanager.delivery

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration

class ConstantReplayCalculatorTest {

    companion object {
        private val CONFIG = ReplayScheduler.ReplaySchedulerConfig.ConstantReplaySchedulerConfig(
            Duration.ofSeconds(1),
            100
        )
    }

    @Test
    fun `ReplayCalculator calculates intervals correctly`() {
        val calculator = ConstantReplayCalculator(false, CONFIG)
        val firstInterval = calculator.calculateReplayInterval()
        assertEquals(CONFIG.replayPeriod, firstInterval)
        val secondInterval = calculator.calculateReplayInterval(firstInterval)
        assertEquals(CONFIG.replayPeriod, secondInterval)
        val thirdInterval = calculator.calculateReplayInterval(secondInterval)
        assertEquals(CONFIG.replayPeriod, thirdInterval)
    }
}