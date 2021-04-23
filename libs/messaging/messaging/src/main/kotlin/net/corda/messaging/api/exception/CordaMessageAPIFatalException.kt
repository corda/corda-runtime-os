package net.corda.messaging.api.exception

import java.lang.Exception
import java.lang.RuntimeException

class CordaMessageAPIFatalException (message: String, exception: Exception) : RuntimeException(message, exception)

class CordaMessageAPIIntermittentException (message: String, exception: Exception) : RuntimeException(message, exception)