package net.corda.v5.ledger.common.transaction

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Indicates that the signing attempt failed due to no available signing keys.
 */
class TransactionNoAvailableKeysException(message: String, cause: Throwable?) : CordaRuntimeException(message, cause)