package net.corda.securitymanager

import org.osgi.service.permissionadmin.PermissionInfo

/** A service for starting a Corda security manager. */
interface SecurityManagerService {
    /**
     * Starts either the discovery or the restrictive security manager, based on the `isDiscoveryMode` flag.
     *
     * Replaces the existing Corda security manager, if one is already installed.
     */
    fun start(isDiscoveryMode: Boolean = false)

    /**
     * Grants the permissions described by the [permInfos] to the bundles matching the [filter].
     *
     * Throws [SecurityManagerException] if no Corda security manager is currently running.
     */
    fun grantPermission(filter: String, permInfos: List<PermissionInfo>)
}