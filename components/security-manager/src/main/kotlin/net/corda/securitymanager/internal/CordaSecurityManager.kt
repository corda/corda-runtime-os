package net.corda.securitymanager.internal

import net.corda.securitymanager.ConditionalPermission
import org.osgi.service.condpermadmin.ConditionalPermissionAdmin
import java.security.Permission

/** Common interface for all Corda security managers. */
interface CordaSecurityManager {
    /** Perform any clean-up required before replacing this [CordaSecurityManager] with another. */
    fun stop()

    /** Grants the permissions described by the [perms] to the bundles matching the [filter]. */
    fun grantPermissions(filter: String, perms: Collection<Permission>)

    /** Denies the permissions described by the [perms] to the bundles matching the [filter]. */
    fun denyPermissions(filter: String, perms: Collection<Permission>)

    /**
     * Adds the [perms] to the start of [ConditionalPermissionAdmin]'s permissions list.
     *
     * If [clear] is set, the existing permissions are cleared first.
     */
    fun updateConditionalPerms(perms: Collection<ConditionalPermission>, clear: Boolean = true)
}