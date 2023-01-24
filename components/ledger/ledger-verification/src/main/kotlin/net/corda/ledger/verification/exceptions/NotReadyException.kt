package net.corda.ledger.verification.exceptions

/**
 * Used to indicate that a db worker is not yet ready.
 *
 * This can happen if cpks broadcast over kafka have not yet arrived.
 */
class NotReadyException(message: String, cause: Throwable? = null) : Exception(message, cause)
