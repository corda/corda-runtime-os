package net.corda.membership.lib.schema.validation

import net.corda.schema.membership.MembershipSchema
import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Exception thrown when validation using a membership schema fails.
 */
class MembershipSchemaValidationException(
    msg: String,
    cause: Throwable?,
    schema: MembershipSchema,
    errors: List<String>
) : CordaRuntimeException(msg, cause) {
    override val message: String = errors.joinToString(
        prefix = "$msg. Failed to validate against schema \"${schema.schemaName}\" due to the following error(s): [",
        postfix = "]",
        separator = ",${System.lineSeparator()}"
    )
}

/**
 * Exception thrown when a membership schema cannot be retrieved.
 */
class MembershipSchemaFetchException(message: String, cause: Throwable? = null) : CordaRuntimeException(message, cause)
