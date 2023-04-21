package net.corda.testing.securitymanager

import net.corda.securitymanager.ConditionalPermission
import net.corda.securitymanager.ConditionalPermission.Access
import net.corda.securitymanager.SecurityManagerService
import org.osgi.service.condpermadmin.BundleLocationCondition
import org.osgi.service.condpermadmin.ConditionInfo
import org.osgi.service.permissionadmin.PermissionInfo
import java.security.Permission

/**
 * Creates [ConditionalPermission] with [BundleLocationCondition] and given [permissions]
 */
fun bundleLocationPermission(location: String, permissions: List<Permission>, access: Access) =
    ConditionalPermission(
        ConditionInfo(BundleLocationCondition::class.java.canonicalName, arrayOf(location)),
        permissions.map { PermissionInfo(it::class.java.canonicalName, it.name, it.actions) }.toTypedArray(),
        access
    )

/**
 * Updates [SecurityManagerService] permissions by granting given [permissions] for specified bundle [location] filter
 */
fun SecurityManagerService.grantPermissions(location: String, permissions: List<Permission>) {
    this.updatePermissions(listOf(
        bundleLocationPermission(location, permissions, Access.ALLOW)),
        clear = false
    )
}

/**
 * Updates [SecurityManagerService] permissions by denying given [permissions] for specified bundle [location] filter
 */
fun SecurityManagerService.denyPermissions(location: String, permissions: List<Permission>) {
    this.updatePermissions(listOf(
        bundleLocationPermission(location, permissions, Access.DENY)),
        clear = false
    )
}
