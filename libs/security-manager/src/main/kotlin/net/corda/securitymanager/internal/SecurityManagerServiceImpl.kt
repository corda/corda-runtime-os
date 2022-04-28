package net.corda.securitymanager.internal

import net.corda.securitymanager.SecurityManagerException
import net.corda.securitymanager.SecurityManagerService
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.condpermadmin.ConditionalPermissionAdmin
import java.security.Permission

/** An implementation of [SecurityManagerService]. */
@Suppress("unused")
@Component(immediate = true, service = [SecurityManagerService::class])
class SecurityManagerServiceImpl @Activate constructor(
    @Reference
    private val conditionalPermissionAdmin: ConditionalPermissionAdmin,
    @Reference
    private val bundleUtils: BundleUtils,
    @Reference
    private val logUtils: LogUtils
) : SecurityManagerService {
    companion object {
        private val log = contextLogger()
    }

    // The OSGi security manager that is installed at framework start. This may be temporarily replaced by the
    // `DiscoverySecurityManager`.
    private val osgiSecurityManager: SecurityManager? = System.getSecurityManager()

    // The current Corda security manager.
    private var cordaSecurityManager: CordaSecurityManager? = null

    override fun start() {
        cordaSecurityManager?.stop()
        log.info("Starting restrictive Corda security manager.")
        cordaSecurityManager = RestrictiveSecurityManager(conditionalPermissionAdmin, osgiSecurityManager)
    }

    override fun startDiscoveryMode(prefixes: Collection<String>) {
        cordaSecurityManager?.stop()
        log.info("Starting discovery Corda security manager. This is not secure in production.")
        cordaSecurityManager = DiscoverySecurityManager(prefixes, bundleUtils, logUtils)
    }

    override fun grantPermissions(filter: String, perms: Collection<Permission>) =
        cordaSecurityManager?.grantPermissions(filter, perms)
            ?: throw SecurityManagerException("No Corda security manager is currently running.")

    override fun denyPermissions(filter: String, perms: Collection<Permission>) =
        cordaSecurityManager?.denyPermissions(filter, perms)
            ?: throw SecurityManagerException("No Corda security manager is currently running.")
}