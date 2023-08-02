package net.corda.processors.evm

import net.corda.v5.base.exceptions.CordaRuntimeException

/** Exceptions related to the [EVMProcessor]. */
class EVMProcessorException(message: String, e: Exception? = null) : CordaRuntimeException(message, e)