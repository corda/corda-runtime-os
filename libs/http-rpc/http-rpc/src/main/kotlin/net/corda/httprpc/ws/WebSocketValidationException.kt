package net.corda.httprpc.ws

import net.corda.v5.base.exceptions.CordaRuntimeException

class WebSocketValidationException(message: String, cause: Throwable) : CordaRuntimeException(message, cause)