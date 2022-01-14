package net.corda.processors.db

import net.corda.v5.base.exceptions.CordaRuntimeException

/** Exceptions related to the [DBProcessor]. */
class DBProcessorException(message: String, e: Exception? = null) : CordaRuntimeException(message, e)