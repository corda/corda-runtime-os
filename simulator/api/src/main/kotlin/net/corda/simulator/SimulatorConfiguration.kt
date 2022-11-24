package net.corda.simulator

import net.corda.simulator.factories.ServiceOverrideBuilder
import java.time.Clock
import java.time.Duration

/**
 * Configuration for Simulator. This interface may be implemented directly or built using a
 * [net.corda.simulator.factories.SimulatorConfigurationBuilder].
 */
interface SimulatorConfiguration {

    /**
     * The interval at which to check responder flow health.
     */
    val pollInterval: Duration

    /**
     * The maximum length of time to wait for a responder flow to respond.
     */
    val timeout: Duration

    /**
     * The clock to use for time-related functions in Simulator.
     */
    val clock : Clock

    /**
     * A map of services to be overridden against the builders for the new services.
     */
    val serviceOverrides: Map<Class<*>, ServiceOverrideBuilder<*>>
}
