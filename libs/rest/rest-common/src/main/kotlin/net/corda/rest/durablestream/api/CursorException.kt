package net.corda.rest.durablestream.api

import net.corda.v5.base.exceptions.CordaRuntimeException

class CursorException(message: String, cause: Exception?) : CordaRuntimeException(message, cause)
