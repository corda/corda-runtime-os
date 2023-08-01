package net.corda.membership.lib.exceptions

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Thrown to indicate that there was an exception during a membership persistence operation.
 */
open class MembershipPersistenceException(err: String, cause: Throwable? = null) : CordaRuntimeException(err, cause)

/**
 * Thrown to indicate a failed manual check on the entity to be updated or deleted, e.g. version check, during a
 * membership persistence operation.
 */
class InvalidEntityUpdateException(err: String) : MembershipPersistenceException(err)