package net.corda.simulator.runtime.flows

import net.corda.simulator.runtime.utils.getProtocolOrNull
import net.corda.v5.application.flows.Flow

data class FlowAndProtocol(val flow: Flow, val protocol: String? = flow.getProtocolOrNull())