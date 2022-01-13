package net.corda.configuration.rpcops

import net.corda.v5.base.exceptions.CordaRuntimeException

/** Exceptions related to the [ConfigRPCOpsService]. */
class ConfigRPCOpsServiceException(message: String, e: Exception? = null) : CordaRuntimeException(message, e)