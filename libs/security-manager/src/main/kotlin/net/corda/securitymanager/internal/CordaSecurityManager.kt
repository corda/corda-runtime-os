package net.corda.securitymanager.internal

import java.security.Permission

/** Common interface for all Corda security managers. */
interface CordaSecurityManager {
    /** Perform any clean-up required before replacing this [CordaSecurityManager] with another. */
    fun stop()

    /** Grants the permissions described by the [perms] to the bundles matching the [filter]. */
    fun grantPermissions(filter: String, perms: Collection<Permission>)

    /** Denies the permissions described by the [perms] to the bundles matching the [filter]. */
    fun denyPermissions(filter: String, perms: Collection<Permission>)
}