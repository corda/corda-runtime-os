package net.corda.securitymanager.internal

import net.corda.securitymanager.ALL
import net.corda.securitymanager.CAPABILITY_PERMISSION_NAME
import net.corda.securitymanager.PACKAGE_PERMISSION_NAME
import net.corda.securitymanager.SANDBOX_SECURITY_DOMAIN_FILTER
import net.corda.securitymanager.SERVICE_PERMISSION_NAME
import net.corda.securitymanager.SecurityManagerException
import org.osgi.framework.CapabilityPermission.PROVIDE
import org.osgi.framework.CapabilityPermission.REQUIRE
import org.osgi.framework.FrameworkUtil
import org.osgi.framework.PackagePermission.EXPORTONLY
import org.osgi.framework.PackagePermission.IMPORT
import org.osgi.framework.ServicePermission.GET
import org.osgi.framework.ServicePermission.REGISTER
import org.osgi.service.cm.ConfigurationAdmin
import org.osgi.service.condpermadmin.BundleLocationCondition
import org.osgi.service.condpermadmin.ConditionInfo
import org.osgi.service.condpermadmin.ConditionalPermissionAdmin
import org.osgi.service.condpermadmin.ConditionalPermissionInfo.ALLOW
import org.osgi.service.condpermadmin.ConditionalPermissionInfo.DENY
import org.osgi.service.permissionadmin.PermissionAdmin
import org.osgi.service.permissionadmin.PermissionInfo
import java.security.AllPermission

/** A [CordaSecurityManager] that grants sandbox code a very limited set of permissions. */
@Suppress("unused")
class RestrictiveSecurityManager(
    permissionAdmin: PermissionAdmin, private val conditionalPermissionAdmin: ConditionalPermissionAdmin
) : CordaSecurityManager {
    companion object {
        private val allPermInfo = PermissionInfo(AllPermission::class.java.name, ALL, ALL)
        private val packagePermission = PermissionInfo(PACKAGE_PERMISSION_NAME, ALL, "$EXPORTONLY,$IMPORT")
        private val capabilityPermission = PermissionInfo(CAPABILITY_PERMISSION_NAME, ALL, "$REQUIRE,$PROVIDE")
        private val servicePermission = PermissionInfo(SERVICE_PERMISSION_NAME, ALL, "$GET,$REGISTER")
    }

    /**
     * Performs two sets of permission updates:
     *
     *  * Grants all permissions to the `ConfigurationAdmin` service. For reasons unknown, the permissive Java
     *   security policy that is applied on framework start-up is not extended to this service
     *
     *  * Denies all permissions to sandbox bundles, except some minimal permissions required to set up OSGi bundles
     *
     *  These permissions work in tandem with the OSGi hooks defined in the `sandbox` module to prevent sandbox bundles
     *  from performing illegal actions.
     */
    init {
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

    /**
     * Denies all permissions to bundles matching the [SANDBOX_SECURITY_DOMAIN_FILTER], except some minimal permissions
     * required to set up OSGi bundles (i.e. [packagePermission], [capabilityPermission] and [servicePermission]).
     */
    private fun restrictSandboxBundlePermissions(conditionalPermissionAdmin: ConditionalPermissionAdmin) {
        // These are the permissions required to set up OSGi bundles correctly:
        //  * Importing and exporting packages
        //  * Requiring and providing OSGi capabilities
        //  * Retrieving and registering services
        val grantOsgiSetupPerms = conditionalPermissionAdmin.newConditionalPermissionInfo(
            null,
            arrayOf(ConditionInfo(BundleLocationCondition::class.java.name, arrayOf(SANDBOX_SECURITY_DOMAIN_FILTER))),
            arrayOf(packagePermission, capabilityPermission, servicePermission),
            ALLOW
        )

        val denyAllPermissionsToSandboxes = conditionalPermissionAdmin.newConditionalPermissionInfo(
            null,
            arrayOf(ConditionInfo(BundleLocationCondition::class.java.name, arrayOf(SANDBOX_SECURITY_DOMAIN_FILTER))),
            arrayOf(allPermInfo),
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
        condPerms.add(grantOsgiSetupPerms)
        condPerms.add(denyAllPermissionsToSandboxes)
        condPerms.add(grantAllPermissions)

        if (!condPermUpdate.commit()) throw SecurityManagerException("Unable to commit updated bundle permissions.")
    }
}