package net.corda.simulator.runtime.config

import net.corda.simulator.SimulatorConfiguration
import net.corda.simulator.factories.ServiceOverrideBuilder
import net.corda.simulator.factories.SimulatorConfigurationBuilder
import net.corda.v5.application.marshalling.json.JsonDeserializer
import net.corda.v5.application.marshalling.json.JsonSerializer
import net.corda.v5.serialization.SerializationCustomSerializer
import java.time.Clock
import java.time.Duration

/**
 * Builder for Simulator configuration.
 *
 * @see SimulatorConfigurationBuilder for details.
 */
class DefaultConfigurationBuilder : SimulatorConfigurationBuilder {

    companion object {
        private const val CORDA_PREFIX = "net.corda.v5"
        private data class SimulatorConfigurationBase (
            override val clock: Clock = Clock.systemDefaultZone(),
            override val timeout: Duration = Duration.ofMinutes(1),
            override val pollInterval: Duration = Duration.ofMillis(100),
            override val serviceOverrides: Map<Class<*>, ServiceOverrideBuilder<*>> = mapOf(),
            override val customSerializers: List<SerializationCustomSerializer<*, *>> = listOf(),
            override val customJsonSerializers: Map<JsonSerializer<*>, Class<*>> = mapOf(),
            override val customJsonDeserializers: Map<JsonDeserializer<*>, Class<*>> = mapOf()
        ) : SimulatorConfiguration
    }

    private var config = SimulatorConfigurationBase()

    override fun withClock(clock: Clock): SimulatorConfigurationBuilder {
        config = config.copy(clock = clock)
        return this
    }

    override fun withPollInterval(pollInterval: Duration): SimulatorConfigurationBuilder {
        config = config.copy(pollInterval = pollInterval)
        return this
    }

    override fun withTimeout(timeout: Duration): SimulatorConfigurationBuilder {
        config = config.copy(timeout = timeout)
        return this
    }

    override fun withServiceOverride(
        serviceClass: Class<*>,
        builder: ServiceOverrideBuilder<*>
    ): SimulatorConfigurationBuilder {
        if(!serviceClass.`package`.name.startsWith(CORDA_PREFIX)) {
            throw IllegalArgumentException(
                "Attempt to override non-Corda service $serviceClass. Custom services are not supported."
            )
        }
        config = config.copy(serviceOverrides = config.serviceOverrides.plus(serviceClass to builder))
        return this
    }

    override fun withCustomSerializer(customSerializer: SerializationCustomSerializer<*, *>)
            : SimulatorConfigurationBuilder {
        config = config.copy(customSerializers = config.customSerializers.plus(customSerializer))
        return this
    }

    override fun withCustomJsonSerializer(
        customJsonSerializer: JsonSerializer<*>,
        type: Class<*>
    ): SimulatorConfigurationBuilder {
        config = config.copy(customJsonSerializers = config.customJsonSerializers
            .plus(customJsonSerializer to type))
        return this
    }

    override fun withCustomJsonDeserializer(
        customJsonDeserializer: JsonDeserializer<*>,
        type: Class<*>
    ): SimulatorConfigurationBuilder {
        config = config.copy(customJsonDeserializers = config.customJsonDeserializers
            .plus(customJsonDeserializer to type))
        return this
    }


    override fun build(): SimulatorConfiguration {
        return config
    }
}
