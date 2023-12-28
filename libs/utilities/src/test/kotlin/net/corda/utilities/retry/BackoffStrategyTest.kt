package net.corda.utilities.retry

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class BackoffStrategyTest {
    @Test
    fun constantBackoffStrategyReturnsConstantDelay() {
        val backoffStrategy = Constant(10)

        repeat(10) {
            assertThat(backoffStrategy.delay(it)).isEqualTo(10)
        }
    }

    @Test
    fun linearBackoffStrategyReturnsLinearDelay() {
        val backoffStrategy = Linear(growthFactor = 1000L)

        assertThat(backoffStrategy.delay(1)).isEqualTo(1.seconds.inWholeMilliseconds)
        assertThat(backoffStrategy.delay(2)).isEqualTo(2.seconds.inWholeMilliseconds)
        assertThat(backoffStrategy.delay(3)).isEqualTo(3.seconds.inWholeMilliseconds)
    }

    @Test
    fun exponentialBackoffStrategyReturnsExponentialDelayWhenUsingDefaults() {
        val backoffStrategy = Exponential()
        assertThat(backoffStrategy.delay(1)).isEqualTo(2.seconds.inWholeMilliseconds)
        assertThat(backoffStrategy.delay(2)).isEqualTo(4.seconds.inWholeMilliseconds)
        assertThat(backoffStrategy.delay(3)).isEqualTo(8.seconds.inWholeMilliseconds)
    }

    @Test
    fun exponentialBackoffStrategyReturnsExponentialDelayWhenUsingCustomValues() {
        val backoffStrategy = Exponential(base = 3.0, growthFactor = 100L)
        assertThat(backoffStrategy.delay(1)).isEqualTo(300.milliseconds.inWholeMilliseconds)
        assertThat(backoffStrategy.delay(2)).isEqualTo(900.milliseconds.inWholeMilliseconds)
        assertThat(backoffStrategy.delay(3)).isEqualTo(2700.milliseconds.inWholeMilliseconds)
    }
}
