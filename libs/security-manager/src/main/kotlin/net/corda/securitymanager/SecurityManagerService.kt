package net.corda.securitymanager

import java.security.Permission

/**
 * A service for starting a Corda security manager.
 *
 * There are two Corda security managers:
 *
 *  * The `RestrictiveSecurityManager`, which provides control over what permissions are granted or denied
 *  * The `DiscoverySecurityManager`, which grants sandbox code all permissions. While active, for any permission check
 *    performed by user code, it writes out the corresponding permission to an updated permissions file
 *
 * The `DiscoverySecurityManager` is not secure for production use.
 */
interface SecurityManagerService {
    /**
     * Starts either the discovery or the restrictive security manager, based on the `isDiscoveryMode` flag.
     *
     * Replaces the existing Corda security manager, if one is already installed.
     */
    fun start()

    /**
     * Starts the discovery security manager.
     *
     * Replaces the existing Corda security manager, if one is already installed.
     */
    fun startDiscoveryMode()

    /**
     * Grants the [perms] to the bundles matching the [filter].
     *
     * Throws [SecurityManagerException] if no Corda security manager is currently running.
     */
    fun grantPermissions(filter: String, perms: Collection<Permission>)

    /**
     * Denies the [perms] to the bundles matching the [filter].
     *
     * Throws [SecurityManagerException] if no Corda security manager is currently running.
     */
    fun denyPermissions(filter: String, perms: Collection<Permission>)
}