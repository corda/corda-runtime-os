package net.corda.securitymanager.internal

import net.corda.securitymanager.SecurityManagerService
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.condpermadmin.ConditionalPermissionAdmin
import org.osgi.service.permissionadmin.PermissionAdmin

/** An implementation of [SecurityManagerService]. */
@Suppress("unused")
@Component(immediate = true, service = [SecurityManagerService::class])
class SecurityManagerServiceImpl @Activate constructor(
    @Reference
    private val permissionAdmin: PermissionAdmin,
    @Reference
    private val conditionalPermissionAdmin: ConditionalPermissionAdmin,
    @Reference
    private val bundleUtils: BundleUtils
) : SecurityManagerService {
    companion object {
        private val log = contextLogger()
    }

    // The current Corda security manager.
    private var cordaSecurityManager: CordaSecurityManager? = null

    @Suppress("unused")
    override fun start() {
        cordaSecurityManager?.stop()
        log.info("Starting restrictive Corda security manager.")
        cordaSecurityManager = RestrictiveSecurityManager(permissionAdmin, conditionalPermissionAdmin)
    }

    override fun startDiscoveryMode(prefixes: Collection<String>) {
        cordaSecurityManager?.stop()
        log.info("Starting discovery Corda security manager. This is not secure in production.")
        cordaSecurityManager = DiscoverySecurityManager(prefixes, bundleUtils)
    }
}