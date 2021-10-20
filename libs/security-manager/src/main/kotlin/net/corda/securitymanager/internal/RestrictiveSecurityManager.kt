package net.corda.securitymanager.internal

import net.corda.securitymanager.SecurityManagerException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.condpermadmin.BundleLocationCondition
import org.osgi.service.condpermadmin.ConditionInfo
import org.osgi.service.condpermadmin.ConditionalPermissionAdmin
import org.osgi.service.condpermadmin.ConditionalPermissionInfo
import org.osgi.service.condpermadmin.ConditionalPermissionInfo.ALLOW
import org.osgi.service.condpermadmin.ConditionalPermissionInfo.DENY
import org.osgi.service.permissionadmin.PermissionAdmin
import org.osgi.service.permissionadmin.PermissionInfo
import java.security.AllPermission
import java.security.Permission

/** A [CordaSecurityManager] that grants sandbox code a very limited set of permissions. */
@Component(service = [RestrictiveSecurityManager::class])
class RestrictiveSecurityManager @Activate constructor(
    @Reference
    private val permissionAdmin: PermissionAdmin,
    @Reference
    private val conditionalPermissionAdmin: ConditionalPermissionAdmin
) : CordaSecurityManager {
    companion object {
        private val allPermInfo = PermissionInfo(AllPermission::class.java.name, ALL, ALL)
    }

    /** Grants all permissions to all bundles. */
    override fun start() {
        val grantAllPermissions = conditionalPermissionAdmin.newConditionalPermissionInfo(
            null, null, arrayOf(allPermInfo), ALLOW
        )

        updateConditionalPerms(setOf(grantAllPermissions))
    }

    /** Clears any permissions from the [ConditionalPermissionAdmin]. */
    override fun stop() {
        updateConditionalPerms(emptySet())
    }

    override fun grantPermission(filter: String, perms: Collection<Permission>) {
        val permInfos = perms.map { perm -> PermissionInfo(perm::class.java.name, perm.name, perm.actions) }
        val conditionInfo = ConditionInfo(BundleLocationCondition::class.java.name, arrayOf(filter))

        val condPermInfo = conditionalPermissionAdmin.newConditionalPermissionInfo(
            null, arrayOf(conditionInfo), permInfos.toTypedArray(), ALLOW
        )

        updateConditionalPerms(setOf(condPermInfo), clear = false)
    }

    // TODO - Refactor into shared logic with the above.
    override fun denyPermission(filter: String, perms: Collection<Permission>) {
        val permInfos = perms.map { perm -> PermissionInfo(perm::class.java.name, perm.name, perm.actions) }
        val conditionInfo = ConditionInfo(BundleLocationCondition::class.java.name, arrayOf(filter))

        val condPermInfo = conditionalPermissionAdmin.newConditionalPermissionInfo(
            null, arrayOf(conditionInfo), permInfos.toTypedArray(), DENY
        )

        updateConditionalPerms(setOf(condPermInfo), clear = false)
    }
    /**
     * Adds the [perms] to the start of [ConditionalPermissionAdmin]'s permissions list.
     *
     * If [clear] is set, the existing permissions are cleared first.
     */
    private fun updateConditionalPerms(perms: Collection<ConditionalPermissionInfo>, clear: Boolean = true) {
        val condPermUpdate = conditionalPermissionAdmin.newConditionalPermissionUpdate()
        val condPerms = condPermUpdate.conditionalPermissionInfos

        if (clear) condPerms.clear()

        // The ordering of the permissions in the list is important. Permissions earlier in the list take priority.
        condPerms.addAll(0, perms)

        if (!condPermUpdate.commit()) throw SecurityManagerException("Unable to commit updated bundle permissions.")
    }
}