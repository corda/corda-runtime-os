package net.corda.simulator.runtime

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration

class DefaultConfigurationBuilderTest {

    @Test
    fun `should allow configuration to be built using a fluent interface`() {
        val clock = Clock.systemUTC()
        val config = DefaultConfigurationBuilder()
            .withClock(clock)
            .withTimeout(Duration.ofDays(1))
            .withPollInterval(Duration.ofMillis(1000))
            .build()

        assertThat(config.clock, `is`(clock))
        assertThat(config.timeout, `is`(Duration.ofDays(1)))
        assertThat(config.pollInterval, `is`(Duration.ofMillis(1000)))
    }

    @Test
    fun `should provide sensible defaults`() {
        val config = DefaultConfigurationBuilder().build()

        assertThat(config.clock, notNullValue())
        assertThat(config.timeout, `is`(Duration.ofMinutes(1)))
        assertThat(config.pollInterval, `is`(Duration.ofMillis(100)))
    }
}