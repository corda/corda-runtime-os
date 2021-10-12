package net.corda.messaging.api.exception

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Exception that occurred on the sender side of RPC messaging pattern
 */
class CordaRPCAPISenderException(message: String?, exception: Exception? = null) :
    CordaRuntimeException(message, exception)

/**
 * Exception that occurred on the responder side of RPC messaging pattern
 */
class CordaRPCAPIResponderException(message: String?, exception: Exception? = null) :
    CordaRuntimeException(message, exception)
