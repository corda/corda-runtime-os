package net.corda.membership.exceptions

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Thrown to indicate that parsing a group policy from string failed.
 */
class BadGroupPolicyException(message: String, cause: Throwable? = null) : CordaRuntimeException(message, cause)