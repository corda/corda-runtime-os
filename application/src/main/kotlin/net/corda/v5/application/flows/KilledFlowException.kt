package net.corda.v5.application.flows

import net.corda.v5.application.flows.flowservices.FlowEngine
import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * An exception that is thrown when a flow has been killed.
 *
 * This exception can be returned and thrown to RPC clients waiting for the result of a flow's future.
 *
 * It can also be used in conjunction with [FlowEngine.isKilled] to escape long-running computation loops when a flow has been killed.
 */
class KilledFlowException(val id: FlowId, message: String) : CordaRuntimeException(message) {
    constructor(id: FlowId) : this(id, "The flow $id was killed")
}