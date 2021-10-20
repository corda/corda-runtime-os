package net.corda.securitymanager.internal

import java.security.Permission

/** Common interface for all Corda security managers. */
interface CordaSecurityManager {
    /** Start enforcing the permissions associated with this [CordaSecurityManager]. */
    fun start()

    /** Perform any clean-up required before replacing this [CordaSecurityManager] with another. */
    fun stop()

    /** Grants the permissions described by the [perms] to the bundles matching the [filter]. */
    fun grantPermission(filter: String, perms: Collection<Permission>)
}