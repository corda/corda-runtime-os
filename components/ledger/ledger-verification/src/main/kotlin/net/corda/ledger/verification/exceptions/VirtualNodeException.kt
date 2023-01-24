package net.corda.ledger.verification.exceptions

/**
 * Used to indicate a problem with retrieving virtual nodes from the system.
 *
 * This exception may occur because CPI metadata is missing from various
 * internal components because it simply hasn't been sent over Kafka yet.
 */
class VirtualNodeException(message: String, cause: Throwable? = null) : Exception(message, cause)
