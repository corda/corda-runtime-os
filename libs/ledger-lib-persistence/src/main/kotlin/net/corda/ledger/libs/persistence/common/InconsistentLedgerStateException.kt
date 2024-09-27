package net.corda.ledger.libs.persistence.common

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Exception class to signal that the persistence processors found the ledger in an inconsistence
 * state and cannot carry on processing
 */
class InconsistentLedgerStateException(message: String?) : CordaRuntimeException(message)
