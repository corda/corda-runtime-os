package net.corda.membership.registration

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Exception thrown during registration status query.
 */
class RegistrationStatusQueryException(message: String) : CordaRuntimeException(message)
