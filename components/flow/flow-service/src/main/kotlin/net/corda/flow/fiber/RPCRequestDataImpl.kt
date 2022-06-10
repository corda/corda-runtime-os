package net.corda.flow.fiber

import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.serialization.JsonMarshallingService

/**
 * Read the start args from the start context. This prevents the full start args from being serialized on every
 * checkpoint for an RPC started flow.
 */
class RPCRequestDataImpl(private val fiberService: FlowFiberService) : RPCRequestData {
    override fun getRequestBody(): String {
        return fiberService.getExecutingFiber().getExecutionContext().flowCheckpoint.flowStartContext.startArgs
            ?: throw IllegalStateException("Failed to find the start args for RPC started flow")
    }

    override fun <T> getRequestBodyAs(jsonMarshallingService: JsonMarshallingService, clazz: Class<T>): T {
        return jsonMarshallingService.parseJson(getRequestBody(), clazz)
    }
}