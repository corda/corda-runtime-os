package net.corda.membership.client

import net.corda.v5.base.exceptions.CordaRuntimeException

class RegistrationProgressNotFoundException(message: String) : CordaRuntimeException(message)
