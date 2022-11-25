package net.corda.simulator.runtime.config

import net.corda.simulator.factories.ServiceOverrideBuilder
import net.corda.v5.application.marshalling.JsonMarshallingService
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import java.time.Clock
import java.time.Duration

class DefaultConfigurationBuilderTest {

    @Test
    fun `should allow configuration to be built using a fluent interface`() {
        val configBuilder = DefaultConfigurationBuilder()

        val clock = mock<Clock>()
        val serviceBuilder = ServiceOverrideBuilder<JsonMarshallingService> { _, _, _ -> mock()}

        val config = configBuilder
            .withClock(clock)
            .withTimeout(Duration.ofDays(1))
            .withPollInterval(Duration.ofMillis(1000))
            .withServiceOverride(JsonMarshallingService::class.java, serviceBuilder)
            .build()

        assertThat(config.clock, `is`(clock))
        assertThat(config.timeout, `is`(Duration.ofDays(1)))
        assertThat(config.pollInterval, `is`(Duration.ofMillis(1000)))
        assertThat(config.serviceOverrides, `is`(mapOf(JsonMarshallingService::class.java to serviceBuilder)))
    }

    @Test
    fun `should throw an exception if a builder is added for something that is not a Corda service`() {
        val badServiceBuilder = ServiceOverrideBuilder<Clock>{ _, _, _ -> mock()}
        val configBuilder = DefaultConfigurationBuilder()
        assertThrows<java.lang.IllegalArgumentException> {
            configBuilder.withServiceOverride(Clock::class.java, badServiceBuilder)
        }
    }

    @Test
    fun `should provide sensible defaults`() {
        val config = DefaultConfigurationBuilder().build()

        assertThat(config.clock, Matchers.notNullValue())
        assertThat(config.timeout, `is`(Duration.ofMinutes(1)))
        assertThat(config.pollInterval, `is`(Duration.ofMillis(100)))
        assertThat(config.serviceOverrides, `is`(mapOf()))
    }
}