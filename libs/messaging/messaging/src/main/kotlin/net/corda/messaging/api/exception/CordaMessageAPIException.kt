package net.corda.messaging.api.exception

import java.lang.Exception
import java.lang.RuntimeException

class CordaMessageAPIException (message: String, exception: Exception) : RuntimeException(message, exception)