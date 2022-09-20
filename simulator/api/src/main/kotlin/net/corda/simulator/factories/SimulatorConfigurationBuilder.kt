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
     * @param clock the clock with which to build the configuration
     * @return a copy of this builder, with the given clock
     */
    fun withClock(clock: Clock): SimulatorConfigurationBuilder

    /**
     * @param pollInterval the interval at which to check for responder flow health
     * @return a copy of this builder, with the given poll interval
     */
    fun withPollInterval(pollInterval: Duration): SimulatorConfigurationBuilder

    /**
     * @param timeout the maximum length of time to wait for a responder flow to respond
     * @return a copy of this builder, with the given timeout
     */
    fun withTimeout(timeout: Duration): SimulatorConfigurationBuilder

    /**
     * @return configuration built using chosen options or defaults if no overriding options have been selected
     */
    fun build(): SimulatorConfiguration

    companion object {
        /**
         * @return a factory for building [net.corda.simulator.SimulatorConfiguration]
         */
        fun create(): SimulatorConfigurationBuilder {
            return ServiceLoader.load(SimulatorConfigurationBuilder::class.java).first()
        }
    }
}