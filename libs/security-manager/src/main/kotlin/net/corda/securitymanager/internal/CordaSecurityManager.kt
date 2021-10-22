package net.corda.securitymanager.internal

/** Common interface for all Corda security managers. */
interface CordaSecurityManager {
    /** Perform any clean-up required before replacing this [CordaSecurityManager] with another. */
    fun stop()
}