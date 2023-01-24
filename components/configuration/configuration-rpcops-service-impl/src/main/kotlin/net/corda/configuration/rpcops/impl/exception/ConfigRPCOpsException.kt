package net.corda.configuration.rpcops.impl.exception

import net.corda.v5.base.exceptions.CordaRuntimeException

/** Exceptions related to the [net.corda.configuration.rpcops.impl.v1.ConfigRestResourceImpl]. */
class ConfigRPCOpsException(message: String, e: Exception? = null) : CordaRuntimeException(message, e)