package net.corda.flow.fiber

import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession

/**
 * Represents the flow logic and args of a flow that can be started at the top level.
 *
 * The args will be provided to the call method of the flow where appropriate.
 */
sealed interface FlowLogicAndArgs {
    val logic: Flow<*>

    /**
     * A flow started via RPC
     */
    data class RPCStartedFlow(override val logic: RPCStartableFlow<*>, val requestBody: String) : FlowLogicAndArgs

    /**
     * A flow started via an incoming session event
     */
    data class InitiatedFlow(override val logic: ResponderFlow<*>, val session: FlowSession) : FlowLogicAndArgs
}