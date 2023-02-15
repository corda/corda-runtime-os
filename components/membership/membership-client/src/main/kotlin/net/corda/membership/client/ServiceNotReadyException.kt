package net.corda.membership.client

import net.corda.v5.base.exceptions.CordaRuntimeException

class ServiceNotReadyException(cause: Exception) : CordaRuntimeException(cause.message, cause)
