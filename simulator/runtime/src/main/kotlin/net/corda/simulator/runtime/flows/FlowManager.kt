package net.corda.simulator.runtime.flows

import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.SubFlow

interface FlowManager {

    fun call(requestData: RPCRequestData, flow: RPCStartableFlow) : String
    fun <R> call(subFlow: SubFlow<R>): R
}
