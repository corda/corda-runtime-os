package net.corda.simulator.factories

import net.corda.simulator.SimulatorConfiguration
import java.time.Clock
import java.time.Duration
import java.util.ServiceLoader

/**
 * Builds a configuration with which to call a flow, providing sensible defaults for any value which is
 * not explicitly overridden.
 */
interface SimulatorConfigurationBuilder {

    fun withClock(clock: Clock): SimulatorConfigurationBuilder

    fun withPollInterval(pollInterval: Duration): SimulatorConfigurationBuilder

    fun withTimeout(timeout: Duration): SimulatorConfigurationBuilder

    fun build(): SimulatorConfiguration

    companion object {
        fun create(): SimulatorConfigurationBuilder {
            return ServiceLoader.load(SimulatorConfigurationBuilder::class.java).first()
        }
    }
}