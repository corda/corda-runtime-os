package net.corda.configuration.write

import net.corda.v5.base.exceptions.CordaRuntimeException

/** Exceptions related to the [ConfigWriteService]. */
open class ConfigWriteServiceException(message: String, e: Exception? = null) : CordaRuntimeException(message, e)