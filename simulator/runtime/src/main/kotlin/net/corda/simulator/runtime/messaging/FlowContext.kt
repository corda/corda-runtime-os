package net.corda.simulator.runtime.messaging

import net.corda.simulator.SimulatorConfiguration
import net.corda.v5.base.types.MemberX500Name

data class FlowContext(
    val configuration: SimulatorConfiguration,
    val member: MemberX500Name,
    val protocol: String,
)
