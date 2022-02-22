package net.corda.flow.rpcops

import net.corda.v5.base.exceptions.CordaRuntimeException

/** Exceptions related to the [FlowRPCOpsService]. */
class FlowRPCOpsServiceException(message: String, e: Exception? = null) : CordaRuntimeException(message, e)