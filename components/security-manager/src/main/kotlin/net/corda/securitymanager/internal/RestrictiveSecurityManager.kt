@file:Suppress("deprecation")
package net.corda.securitymanager.internal

import net.corda.securitymanager.ConditionalPermission
import net.corda.securitymanager.ConditionalPermission.Access
import net.corda.securitymanager.SecurityManagerException
import org.osgi.service.condpermadmin.BundleLocationCondition
import org.osgi.service.condpermadmin.ConditionInfo
import org.osgi.service.condpermadmin.ConditionalPermissionAdmin
import org.osgi.service.condpermadmin.ConditionalPermissionInfo
import org.osgi.service.condpermadmin.ConditionalPermissionInfo.ALLOW
import org.osgi.service.condpermadmin.ConditionalPermissionInfo.DENY
import org.osgi.service.permissionadmin.PermissionInfo
import java.security.AllPermission
import java.security.Permission

/** A [CordaSecurityManager] that provides control over what permissions are granted or denied. */
class RestrictiveSecurityManager(
    private val conditionalPermissionAdmin: ConditionalPermissionAdmin,
    osgiSecurityManager: SecurityManager?
) : CordaSecurityManager {
    companion object {
        private val allPermInfo = PermissionInfo(AllPermission::class.java.name, "*", "*")
    }

    init {
        // Ensure OSGi's SecurityManager is installed.
        if (System.getSecurityManager() !== osgiSecurityManager) {
            System.setSecurityManager(osgiSecurityManager)
        }

        /** Grants all permissions to all bundles. */
        val grantAllPermissions = conditionalPermissionAdmin.newConditionalPermissionInfo(
            null, null, arrayOf(allPermInfo), ALLOW
        )
        updateConditionalPermsInfo(setOf(grantAllPermissions), clear = true)
    }

    /** Clears any permissions from the [ConditionalPermissionAdmin]. */
    override fun stop() = updateConditionalPerms(emptySet())

    override fun grantPermissions(filter: String, perms: Collection<Permission>) =
        modifyPermissions(filter, perms, grant = true)

    override fun denyPermissions(filter: String, perms: Collection<Permission>) =
        modifyPermissions(filter, perms, grant = false)

    override fun updateConditionalPerms(perms: Collection<ConditionalPermission>, clear: Boolean) {
        val permsInfo = perms.map { conditionalPermissionAdmin.newConditionalPermissionInfo(
            it.name, it.conditions, it.permissions, if (it.access == Access.ALLOW) ALLOW else DENY
        )}
        updateConditionalPermsInfo(permsInfo, clear)
    }

    /**
     * Grants or denies the permissions described by the [perms] to the bundles matching the [filter], based on whether
     * [grant] is set.
     */
    private fun modifyPermissions(filter: String, perms: Collection<Permission>, grant: Boolean) {
        val action = if (grant) ALLOW else DENY
        val permInfos = perms.map { perm -> PermissionInfo(perm::class.java.name, perm.name, perm.actions) }
        val conditionInfo = ConditionInfo(BundleLocationCondition::class.java.name, arrayOf(filter))

        val condPermInfo = conditionalPermissionAdmin.newConditionalPermissionInfo(
            null, arrayOf(conditionInfo), permInfos.toTypedArray(), action
        )

        updateConditionalPermsInfo(setOf(condPermInfo), clear = false)
    }

    /**
     * Adds the [perms] to the start of [ConditionalPermissionAdmin]'s permissions list.
     *
     * If [clear] is set, the existing permissions are cleared first.
     */
    private fun updateConditionalPermsInfo(perms: Collection<ConditionalPermissionInfo>, clear: Boolean) {

        val condPermUpdate = conditionalPermissionAdmin.newConditionalPermissionUpdate()
        val condPerms = condPermUpdate.conditionalPermissionInfos

        if (clear) condPerms.clear()

        // The ordering of the permissions in the list is important. Permissions earlier in the list take priority.
        condPerms.addAll(0, perms)

        if (!condPermUpdate.commit()) throw SecurityManagerException("Unable to commit updated bundle permissions.")
    }
}