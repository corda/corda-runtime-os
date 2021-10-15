package net.corda.securitymanager.internal

import net.corda.securitymanager.SecurityManagerException
import org.osgi.framework.AdminPermission
import org.osgi.framework.AdminPermission.EXECUTE
import org.osgi.framework.AdminPermission.EXTENSIONLIFECYCLE
import org.osgi.framework.AdminPermission.LIFECYCLE
import org.osgi.framework.AdminPermission.RESOLVE
import org.osgi.framework.FrameworkUtil
import org.osgi.service.cm.ConfigurationAdmin
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.condpermadmin.BundleLocationCondition
import org.osgi.service.condpermadmin.ConditionInfo
import org.osgi.service.condpermadmin.ConditionalPermissionAdmin
import org.osgi.service.condpermadmin.ConditionalPermissionInfo.ALLOW
import org.osgi.service.condpermadmin.ConditionalPermissionInfo.DENY
import org.osgi.service.permissionadmin.PermissionAdmin
import org.osgi.service.permissionadmin.PermissionInfo
import java.security.AllPermission

/** A [CordaSecurityManager] that grants sandbox code a very limited set of permissions. */
@Component(service = [RestrictiveSecurityManager::class])
class RestrictiveSecurityManager @Activate constructor(
    @Reference
    private val permissionAdmin: PermissionAdmin,
    @Reference
    private val conditionalPermissionAdmin: ConditionalPermissionAdmin
) : CordaSecurityManager {

    companion object {
        // The admin permissions disallowed for sandboxed bundles.
        private const val SANDBOX_ADMIN_PERMISSIONS = "$EXECUTE,$EXTENSIONLIFECYCLE,$LIFECYCLE,$RESOLVE"

        // The standard filter for identifying sandbox bundles based on their location.
        private const val SANDBOX_BUNDLE_FILTER = "sandbox/*"

        private val sandboxAdminPermInfo =
            PermissionInfo(AdminPermission::class.java.name, "*", SANDBOX_ADMIN_PERMISSIONS)
        private val allPermInfo = PermissionInfo(AllPermission::class.java.name, "*", "*")
    }

    /**
     * Performs two sets of permission updates:
     *
     *  * Grants all permissions to the `ConfigurationAdmin`]` service. For reasons unknown, the permissive Java
     *   security policy that is applied on framework start-up is not extended to this service
     *
     *  * Prevents any sandboxed bundles from performing actions that are considered dangerous, by denying them
     *  the `EXECUTE`, `EXTENSIONLIFECYCLE`, `LIFECYCLE` and `RESOLVE` permissions
     *
     *  Note that these permissions work in tandem with the OSGi hooks defined in the `sandbox` module. Those
     *  hooks provide additional security (e.g. by preventing a sandboxed bundles from seeing specific services)
     */
    override fun start() {
        grantConfigAdminPermissions(permissionAdmin)
        restrictSandboxBundlePermissions(conditionalPermissionAdmin)
    }

    /** Clears any permissions from the [ConditionalPermissionAdmin]. */
    override fun stop() {
        val condPermUpdate = conditionalPermissionAdmin.newConditionalPermissionUpdate()
        condPermUpdate.conditionalPermissionInfos.clear()
        if (!condPermUpdate.commit()) throw SecurityManagerException("Unable to commit updated bundle permissions.")
    }

    /** Grants all permissions to the [ConfigurationAdmin] service. */
    private fun grantConfigAdminPermissions(permissionAdmin: PermissionAdmin) {
        permissionAdmin.setPermissions(
            FrameworkUtil.getBundle(ConfigurationAdmin::class.java).location,
            arrayOf(allPermInfo)
        )
    }

    /** Denies specific admin permissions to the bundles matching the [SANDBOX_BUNDLE_FILTER]. */
    private fun restrictSandboxBundlePermissions(conditionalPermissionAdmin: ConditionalPermissionAdmin) {
        val denyAdminPermissions = conditionalPermissionAdmin.newConditionalPermissionInfo(
            null,
            arrayOf(ConditionInfo(BundleLocationCondition::class.java.name, arrayOf(SANDBOX_BUNDLE_FILTER))),
            arrayOf(sandboxAdminPermInfo),
            DENY
        )

        val grantAllPermissions = conditionalPermissionAdmin.newConditionalPermissionInfo(
            null,
            null,
            arrayOf(allPermInfo),
            ALLOW
        )

        val condPermUpdate = conditionalPermissionAdmin.newConditionalPermissionUpdate()
        val condPerms = condPermUpdate.conditionalPermissionInfos
        condPerms.clear()

        // The ordering of the permissions in the list is important. Permissions earlier in the list take priority.
        condPerms.add(denyAdminPermissions)
        condPerms.add(grantAllPermissions)

        if (!condPermUpdate.commit()) throw SecurityManagerException("Unable to commit updated bundle permissions.")
    }
}