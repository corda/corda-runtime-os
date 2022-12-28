package net.corda.simulator.runtime.config

import net.corda.simulator.factories.ServiceOverrideBuilder
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.marshalling.json.JsonDeserializer
import net.corda.v5.application.marshalling.json.JsonSerializer
import net.corda.v5.serialization.SerializationCustomSerializer
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
    fun `should be able to add custom serializers to configuration`(){
        val customSerializer1 = mock<SerializationCustomSerializer<*, *>>()
        val customSerializer2 = mock<SerializationCustomSerializer<*, *>>()
        val configuration = DefaultConfigurationBuilder()
            .withCustomSerializer(customSerializer1)
            .withCustomSerializer(customSerializer2)
            .build()

        assertThat(configuration.customSerializers.size, `is`(2))
        assertThat(configuration.customSerializers, `is`(listOf(customSerializer1, customSerializer2)))
    }

    @Test
    fun `should be able to add custom json serializers to configuration`(){
        val customJsonSerializer = mock<JsonSerializer<Any>>()
        val type = Any::class.java
        val configuration = DefaultConfigurationBuilder()
            .withCustomJsonSerializer(customJsonSerializer, type)
            .build()

        assertThat(configuration.customJsonSerializers, `is`(mapOf(customJsonSerializer to type)))
    }

    @Test
    fun `should be able to add custom json deserializers to configuration`(){
        val customJsonDeserializer = mock<JsonDeserializer<Any>>()
        val type = Any::class.java
        val configuration = DefaultConfigurationBuilder()
            .withCustomJsonDeserializer(customJsonDeserializer, type)
            .build()

        assertThat(configuration.customJsonDeserializers, `is`(mapOf(customJsonDeserializer to type)))
    }

    @Test
    fun `should provide sensible defaults`() {
        val config = DefaultConfigurationBuilder().build()

        assertThat(config.clock, Matchers.notNullValue())
        assertThat(config.timeout, `is`(Duration.ofMinutes(1)))
        assertThat(config.pollInterval, `is`(Duration.ofMillis(100)))
        assertThat(config.serviceOverrides, `is`(mapOf()))
        assertThat(config.customSerializers, `is`(listOf()))
        assertThat(config.customJsonSerializers, `is`(mapOf()))
        assertThat(config.customJsonDeserializers, `is`(mapOf()))
    }
}