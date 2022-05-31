package net.corda.messaging.api.exception

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Exception thrown when there is an error trying to resolve the messaging config.
 */
class CordaMessageAPIConfigException(msg: String, cause: Exception? = null) :
    CordaRuntimeException(msg, cause)
