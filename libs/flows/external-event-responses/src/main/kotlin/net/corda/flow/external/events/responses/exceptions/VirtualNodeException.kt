package net.corda.flow.external.events.responses.exceptions

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Used to indicate a problem with retrieving virtual nodes from the system.
 *
 * This exception may occur because CPI metadata is missing from various
 * internal components because it simply hasn't been sent over Kafka yet.
 */
class VirtualNodeException(message: String, cause: Throwable? = null) : CordaRuntimeException(message, cause)
