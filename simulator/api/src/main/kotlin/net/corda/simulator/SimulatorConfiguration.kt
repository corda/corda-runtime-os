package net.corda.simulator

import java.time.Clock
import java.time.Duration

/**
 * Configuration for Simulator. This interface may be implemented directly or built using a
 * [net.corda.simulator.factories.SimulatorConfigurationBuilder].
 */
interface SimulatorConfiguration {

    /**
     * The interval at which to check for responder flow health.
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

}
