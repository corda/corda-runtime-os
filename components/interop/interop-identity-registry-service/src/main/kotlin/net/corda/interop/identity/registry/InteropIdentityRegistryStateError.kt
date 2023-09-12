package net.corda.interop.identity.registry

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * This error is used when identities are read from the registry to signal that the registry has
 * reached an invalid state. e.g. multiple owned interop identities within a group, duplicate application names etc.
 *
 * The registry is not supposed to reach these states, but may do so if incorrect data is written to the interop
 * identities topic e.g. due to insufficient checks or a race-condition through kafka.
 */
class InteropIdentityRegistryStateError(msg: String, cause: Throwable? = null): CordaRuntimeException(msg, cause)
