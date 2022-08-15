package net.corda.httprpc.ws

import net.corda.v5.base.exceptions.CordaRuntimeException

open class WebSocketValidationException(message: String, cause: Throwable? = null) : CordaRuntimeException(message, cause)