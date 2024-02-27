package net.corda.utilities.retry

import net.corda.v5.base.exceptions.CordaRuntimeException

class RetryException(message: String, cause: Throwable? = null) : CordaRuntimeException(message, cause)
