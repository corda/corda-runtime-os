package net.corda.schema.membership.provider

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Exception thrown when requested config schema is not available.
 */
class MembershipSchemaException(msg: String) : CordaRuntimeException(msg)