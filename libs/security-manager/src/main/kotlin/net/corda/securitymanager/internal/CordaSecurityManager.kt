package net.corda.securitymanager.internal

/** Common interface for all Corda security managers. */
interface CordaSecurityManager {
    /** Start enforcing the permissions associated with this [CordaSecurityManager]. */
    fun start()
}