package net.corda.messagebus.db.persistence

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Thrown when an attempt is made to commit offsets that have already been committed.
 */
class OffsetsAlreadyCommittedException: CordaRuntimeException("Offsets were already committed.")
