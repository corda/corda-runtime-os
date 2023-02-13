package net.corda.messaging.api.exception

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Exception that occurred on the sender side of REST messaging pattern
 */
class CordaRestAPISenderException(message: String?, exception: Throwable? = null) :
    CordaRuntimeException(message, exception)

/**
 * Exception that occurred when a repartition event occurred in that REST messaging pattern
 */
class CordaRestAPIPartitionException(message: String?, exception: Throwable? = null) :
    CordaRuntimeException(message, exception)

/**
 * Exception that occurred on the responder side of REST messaging pattern
 */
class CordaRestAPIResponderException(val errorType: String, message: String?, exception: Throwable? = null) :
    CordaRuntimeException(message, exception)
