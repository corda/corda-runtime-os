package net.corda.flow.fiber

import net.corda.v5.application.flows.RPCRequestData

/**
 * Read the start args from the start context. This prevents the full start args from being serialized on every
 * checkpoint for an RPC started flow.
 */
class RPCRequestDataImpl(private val fiberService: FlowFiberService) : RPCRequestData {
    override fun getRequestBody(): String {
        return fiberService.getExecutingFiber().getExecutionContext().flowCheckpoint.flowStartContext.startArgs
            ?: throw IllegalStateException("Failed to find the start args for RPC started flow")
    }
}