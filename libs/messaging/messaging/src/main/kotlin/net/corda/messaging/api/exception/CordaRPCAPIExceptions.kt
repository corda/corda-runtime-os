package net.corda.messaging.api.exception

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Exception that occurred on the sender side of RPC messaging pattern
 */
class CordaRPCAPISenderException(message: String?, exception: Throwable? = null) :
    CordaRuntimeException(message, exception)

/**
 * Exception that occurred when a repartition event occurred in that RPC messaging pattern
 */
class CordaRPCAPIPartitionException(message: String?, exception: Throwable? = null) :
    CordaRuntimeException(message, exception)

/**
 * Exception that occurred on the responder side of RPC messaging pattern
 */
class CordaRPCAPIResponderException(val errorType: String, message: String?, exception: Throwable? = null) :
    CordaRuntimeException(message, exception)
