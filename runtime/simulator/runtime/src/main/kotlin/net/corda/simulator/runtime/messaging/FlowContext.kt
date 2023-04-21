package net.corda.simulator.runtime.messaging

import net.corda.simulator.SimulatorConfiguration
import net.corda.v5.base.types.MemberX500Name

/**
 * The context in which flow messaging is being called.
 */
data class FlowContext(
    /**
     * Simulator's configuration.
     */
    val configuration: SimulatorConfiguration,

    /**
     * The member for whom the flow has been constructed.
     */
    val member: MemberX500Name,

    /**
     * The protocol of the flow.
     */
    val protocol: String,
)
