package net.corda.securitymanager

import org.osgi.service.condpermadmin.ConditionalPermissionInfo
import java.io.IOException
import java.io.InputStream

/**
 * A service for starting a Corda security manager.
 *
 * Current implementations of Corda security managers:
 *
 *  * The `RestrictiveSecurityManager`, which provides control over what permissions are granted or denied
 */
interface SecurityManagerService {
    /**
     * Starts the restrictive security manager.
     *
     * Replaces the existing Corda security manager, if one is already installed.
     */
    fun startRestrictiveMode()

    /**
     *
     * Adds [permissions] to the start of permissions list.
     *
     * If [clear] is set, the existing permissions are cleared first.
     */
    fun updatePermissions(permissions: List<ConditionalPermission>, clear: Boolean = true)

    /**
     * Reads security policy from [inputStream] and returns list of [ConditionalPermissionInfo]. Policy contains text
     * block(s) of encoded [ConditionalPermissionInfo], for example:
     *
     *     ALLOW {
     *     [org.osgi.service.condpermadmin.BundleLocationCondition "FLOW/ *"]
     *     (java.io.FilePermission "<<ALL FILES>>" "read")
     *     } "Allow read access to all files"
     *
     * @param inputStream Input stream
     * @return List of [ConditionalPermissionInfo]
     */
    @Throws(IOException::class)
    fun readPolicy(inputStream: InputStream): List<ConditionalPermission>
}