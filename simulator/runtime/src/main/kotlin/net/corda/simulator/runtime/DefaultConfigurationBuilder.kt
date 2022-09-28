package net.corda.simulator.runtime

import net.corda.simulator.SimulatorConfiguration
import net.corda.simulator.factories.SimulatorConfigurationBuilder
import java.time.Clock
import java.time.Duration

/**
 * Builder for Simulator configuration.
 *
 * @see SimulatorConfigurationBuilder for details.
 */
class DefaultConfigurationBuilder : SimulatorConfigurationBuilder {

    companion object {
        private data class SimulatorConfigurationBase (
            override val clock: Clock = Clock.systemDefaultZone(),
            override val timeout: Duration = Duration.ofMinutes(1),
            override val pollInterval: Duration = Duration.ofMillis(100)
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

    override fun build(): SimulatorConfiguration {
        return config
    }


}
