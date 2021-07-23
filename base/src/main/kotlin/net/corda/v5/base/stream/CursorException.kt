package net.corda.v5.base.stream

import net.corda.v5.base.exceptions.CordaRuntimeException

class CursorException(message: String, cause: Exception?) : CordaRuntimeException(message, cause)