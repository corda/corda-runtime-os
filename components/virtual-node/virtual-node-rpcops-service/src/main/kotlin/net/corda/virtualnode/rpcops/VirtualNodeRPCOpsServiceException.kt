package net.corda.virtualnode.rpcops

import net.corda.v5.base.exceptions.CordaRuntimeException

/** Exceptions related to the [VirtualNodeRPCOpsService]. */
class VirtualNodeRPCOpsServiceException(message: String, e: Exception? = null) : CordaRuntimeException(message, e)