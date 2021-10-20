package net.corda.securitymanager

import java.security.Permission

/** A service for starting a Corda security manager. */
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
    fun grantPermission(filter: String, perms: Collection<Permission>)

    /**
     * Denies the [perms] to the bundles matching the [filter].
     *
     * Throws [SecurityManagerException] if no Corda security manager is currently running.
     */
    fun denyPermission(filter: String, perms: Collection<Permission>)
}