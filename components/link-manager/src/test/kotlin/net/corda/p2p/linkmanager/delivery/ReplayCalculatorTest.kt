package net.corda.p2p.linkmanager.delivery

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class ReplayCalculatorTest {

    companion object {
        private val CONFIG = ReplayScheduler.ReplaySchedulerConfig(
            Duration.ofSeconds(1),
            Duration.ofSeconds(7),
            100
        )
    }

    @Test
    fun `ReplayCalculator always replays messages if no limit`() {
        val calculator = object: ReplayCalculator(false, CONFIG) {
            override fun calculateReplayInterval(lastDelay: Duration): Duration {
                return Duration.ZERO
            }
        }
        assertTrue(calculator.shouldReplayMessage(0))
        assertTrue(calculator.shouldReplayMessage(CONFIG.maxReplayingMessages))
        assertTrue(calculator.shouldReplayMessage(CONFIG.maxReplayingMessages + 1))
        assertEquals(0, calculator.extraMessagesToReplay(0))
    }

    @Test
    fun `ReplayCalculator only replays messages smaller than the limit`() {
        val calculator =  object: ReplayCalculator(true, CONFIG) {
            override fun calculateReplayInterval(lastDelay: Duration): Duration {
                return Duration.ZERO
            }
        }
        assertTrue(calculator.shouldReplayMessage(0))
        assertTrue(calculator.shouldReplayMessage(CONFIG.maxReplayingMessages - 1))
        assertFalse(calculator.shouldReplayMessage(CONFIG.maxReplayingMessages))
        assertFalse(calculator.shouldReplayMessage(CONFIG.maxReplayingMessages + 1))
        assertEquals(25, calculator.extraMessagesToReplay(75))
    }
}