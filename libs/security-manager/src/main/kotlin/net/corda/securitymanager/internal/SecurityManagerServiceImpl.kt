package net.corda.securitymanager.internal

import net.corda.securitymanager.SecurityManagerException
import net.corda.securitymanager.SecurityManagerService
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.permissionadmin.PermissionInfo
import java.security.Permission

/** An implementation of [SecurityManagerService]. */
@Suppress("unused")
@Component(immediate = true, service = [SecurityManagerService::class])
class SecurityManagerServiceImpl @Activate constructor(
    @Reference
    private val discoverySecurityManager: DiscoverySecurityManager,
    @Reference
    private val restrictiveSecurityManager: RestrictiveSecurityManager
) : SecurityManagerService {
    companion object {
        private val log = contextLogger()
    }

    // The currently-running Corda security manager.
    private var securityManager: CordaSecurityManager? = null

    override fun start(isDiscoveryMode: Boolean) {
        securityManager?.stop()

        securityManager = if (isDiscoveryMode) {
            log.info("Starting discovery Corda security manager. This is not secure in production.")
            discoverySecurityManager
        } else {
            log.info("Starting restrictive Corda security manager.")
            restrictiveSecurityManager
        }.apply { start() }
    }

    override fun grantPermission(filter: String, perms: Collection<Permission>) {
        securityManager?.grantPermission(filter, perms)
            ?: throw SecurityManagerException("No Corda security manager is currently running.")
    }
}