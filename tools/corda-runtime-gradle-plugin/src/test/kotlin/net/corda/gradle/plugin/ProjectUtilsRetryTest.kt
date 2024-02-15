package net.corda.gradle.plugin

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.Duration

class ProjectUtilsRetryTest {
    class Counter {
        private var counter = 0
        fun incrementAndGet() = ++counter
    }

    private val maxRetryAttempts = 5

    @Test
    fun retryAttemptsPositiveAndNegative() {
        assertAll(
            "Invocation counts less that or equal to $maxRetryAttempts should pass, and those greater than, should fail",
            { assertDoesNotThrow("Count 1") { retryUntilExpectedAttempts(1) } },
            { assertDoesNotThrow("Count 4") { retryUntilExpectedAttempts(4) } },
            { assertDoesNotThrow("Count 5") { retryUntilExpectedAttempts(5) } },
            { assertThrows<IllegalArgumentException>("Count 6") { retryUntilExpectedAttempts(6) } },
            { assertThrows<IllegalArgumentException>("Count 10") { retryUntilExpectedAttempts(10) } },
        )
    }

    private fun retryUntilExpectedAttempts(expectedCount: Int) {
        val counter = Counter()
        retryAttempts(attempts = maxRetryAttempts, cooldown = Duration.ofMillis(10)) {
            require(counter.incrementAndGet() == expectedCount)
        }
    }
}
