package net.corda.membership.lib.exceptions

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Thrown to indicate that there was an exception during a membership persistence operation.
 */
open class MembershipPersistenceException(err: String) : CordaRuntimeException(err)