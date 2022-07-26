package net.corda.libs.virtualnode.maintenance.rpcops.impl.v1

import net.corda.v5.base.exceptions.CordaRuntimeException

/** Exceptions related to the [VirtualNodeRPCOpsService]. */
class VirtualNodeRPCMaintenanceOpsServiceException(message: String, e: Exception? = null) :
    CordaRuntimeException(message, e)
