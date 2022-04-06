package net.corda.p2p.linkmanager.delivery

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration

class ConstantReplayCalculatorTest {

    companion object {
        private val CONFIG = ReplayScheduler.ReplaySchedulerConfig(
            Duration.ofSeconds(1),
            Duration.ofSeconds(7),
            100
        )
    }

    @Test
    fun `ReplayCalculator calculates intervals correctly`() {
        val calculator = ConstantReplayCalculator(false, CONFIG)
        val firstInterval = calculator.calculateReplayInterval()
        assertEquals(CONFIG.baseReplayPeriod, firstInterval)
        val secondInterval = calculator.calculateReplayInterval(firstInterval)
        assertEquals(CONFIG.baseReplayPeriod, secondInterval)
        val thirdInterval = calculator.calculateReplayInterval(secondInterval)
        assertEquals(CONFIG.baseReplayPeriod, thirdInterval)
    }
}