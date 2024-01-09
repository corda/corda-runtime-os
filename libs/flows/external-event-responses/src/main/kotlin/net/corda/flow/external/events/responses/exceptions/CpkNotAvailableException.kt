package net.corda.flow.external.events.responses.exceptions

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Used to indicate that a CPKs cannot be retrieved (DB Worker might not be ready yet).
 *
 * This can happen if CPKs broadcast over kafka have not yet arrived.
 */
class CpkNotAvailableException(message: String, cause: Throwable? = null) : CordaRuntimeException(message, cause)
