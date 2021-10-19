package net.corda.securitymanager.internal

import org.osgi.service.permissionadmin.PermissionInfo

/** Common interface for all Corda security managers. */
interface CordaSecurityManager {
    /** Start enforcing the permissions associated with this [CordaSecurityManager]. */
    fun start()

    /** Perform any clean-up required before replacing this [CordaSecurityManager] with another. */
    fun stop()

    /** Grants the permissions described by the [permInfos] to the bundles matching the [filter]. */
    fun grantPermission(filter: String, permInfos: List<PermissionInfo>)
}