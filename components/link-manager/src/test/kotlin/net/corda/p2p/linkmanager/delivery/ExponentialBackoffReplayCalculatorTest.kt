package net.corda.p2p.linkmanager.delivery

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration

class ExponentialBackoffReplayCalculatorTest {

    companion object {
        private val CONFIG = ReplayScheduler.ReplaySchedulerConfig.ExponentialBackoffReplaySchedulerConfig(
            Duration.ofSeconds(1),
            Duration.ofSeconds(7),
            100
        )
    }

    @Test
    fun `ReplayCalculator calculates intervals correctly`() {
        val calculator = ExponentialBackoffReplayCalculator(false, CONFIG)
        val firstInterval = calculator.calculateReplayInterval()
        assertEquals(Duration.ofSeconds(1), firstInterval)
        val secondInterval = calculator.calculateReplayInterval(firstInterval)
        assertEquals(Duration.ofSeconds(2), secondInterval)
        val thirdInterval = calculator.calculateReplayInterval(secondInterval)
        assertEquals(Duration.ofSeconds(4), thirdInterval)
        val fourthInterval = calculator.calculateReplayInterval(thirdInterval)
        assertEquals(Duration.ofSeconds(7), fourthInterval)
    }
}