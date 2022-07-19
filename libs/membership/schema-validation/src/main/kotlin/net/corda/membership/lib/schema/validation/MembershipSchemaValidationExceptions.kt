package net.corda.membership.lib.schema.validation

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Exception thrown when validation using a membership schema fails.
 */
class MembershipSchemaValidationException(msg: String, cause: Throwable? = null) : CordaRuntimeException(msg, cause)

/**
 * Exception thrown when a membership schema cannot be retrieved.
 */
class MembershipSchemaFetchException(message: String, cause: Throwable? = null) : CordaRuntimeException(message, cause)