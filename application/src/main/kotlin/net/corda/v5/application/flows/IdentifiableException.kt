package net.corda.v5.application.flows

/**
 * An exception that may be identified with an ID. If an exception originates in a counter-flow this ID will be
 * propagated. This allows correlation of error conditions across different flows.
 */
interface IdentifiableException {
    /**
     * @return the ID of the error, or null if the error doesn't have it set (yet).
     */
    @JvmDefault
    val errorId: Long? get() = null
}
