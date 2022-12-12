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
     * Registers a builder to use for the given service class in place of the service that Simulator would normally
     * provide.
     *
     * The service parameter passed into the builder will be Simulator's version of the service, or null if
     * Simulator does not yet support the service. The Holding Identity and flow class are also provided to
     * allow finer-grained behaviour in overrides where required.
     *
     * @param service The class of the service to overwrite.
     * @param builder The builder for the replacement service.
     *
     * @throws IllegalArgumentException if the `serviceClass` is not the class of a Corda service.
     */
    fun withServiceOverride(
        serviceClass: Class<*>,
        builder: ServiceOverrideBuilder<*>
    ): SimulatorConfigurationBuilder

    /**
     * @return Configuration built using chosen options or defaults if no overriding options have been selected.
     */
    fun build(): SimulatorConfiguration

    companion object {
        /**
         * @return A factory for building [net.corda.simulator.SimulatorConfiguration].
         */
        @JvmStatic
        fun create(): SimulatorConfigurationBuilder {
            return ServiceLoader.load(SimulatorConfigurationBuilder::class.java).first()
        }
    }
}