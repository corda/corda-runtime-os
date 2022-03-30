package net.corda.messaging.api.exception

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Exception thrown when configuration provided to the patterns library is invalid.
 */
class CordaMessageAPIConfigException(msg: String, cause: Exception? = null) :
    CordaRuntimeException(msg, cause)