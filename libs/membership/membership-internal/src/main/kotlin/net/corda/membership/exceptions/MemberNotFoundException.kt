package net.corda.membership.exceptions

import net.corda.v5.base.exceptions.CordaRuntimeException

class MemberNotFoundException(message: String, cause: Throwable? = null) : CordaRuntimeException(message, cause)