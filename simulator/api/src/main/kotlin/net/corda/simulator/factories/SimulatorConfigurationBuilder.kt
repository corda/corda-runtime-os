package net.corda.simulator.factories

import net.corda.simulator.SimulatorConfiguration
import net.corda.v5.base.annotations.DoNotImplement
import java.time.Clock
import java.time.Duration
import java.util.ServiceLoader

/**
 * Builds a [net.corda.simulator.SimulatorConfiguration] with which to call a flow, providing sensible defaults
 * for any value which is not explicitly overridden.
 */
@DoNotImplement
interface SimulatorConfigurationBuilder {

    /**
     * @param clock The clock with which to build the configuration.
     * @return A copy of this builder, with the given clock.
     */
    fun withClock(clock: Clock): SimulatorConfigurationBuilder

    /**
     * @param pollInterval The interval at which to check for responder flow health.
     * @return A copy of this builder, with the given poll interval.
     */
    fun withPollInterval(pollInterval: Duration): SimulatorConfigurationBuilder

    /**
     * @param timeout The maximum length of time to wait for a responder flow to respond.
     * @return A copy of this builder, with the given timeout.
     */
    fun withTimeout(timeout: Duration): SimulatorConfigurationBuilder

    /**
     * @return Configuration built using chosen options or defaults if no overriding options have been selected.
     */
    fun build(): SimulatorConfiguration

    companion object {
        /**
         * @return A factory for building [net.corda.simulator.SimulatorConfiguration].
         */
        fun create(): SimulatorConfigurationBuilder {
            return ServiceLoader.load(SimulatorConfigurationBuilder::class.java).first()
        }
    }
}