package net.corda.securitymanager.internal

import net.corda.securitymanager.SecurityManagerException
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
    private val conditionalPermissionAdmin: ConditionalPermissionAdmin
): SecurityManagerService {
    companion object {
        private val log = contextLogger()
    }

    private var securityManager: CordaSecurityManager? = null

    /**
     * Starts either the [DiscoverySecurityManager] or the [RestrictiveSecurityManager], based on the [isDiscoveryMode]
     * flag.
     *
     * Throws [SecurityManagerException] if a [CordaSecurityManager] has already been started.
     */
    @Suppress("unused")
    override fun start(isDiscoveryMode: Boolean) {
        if (securityManager != null) {
            throw SecurityManagerException("A Corda security manager has already been started.")
        }

        securityManager = if (isDiscoveryMode) {
            log.info("Starting discovery Corda security manager. This is not secure in production.")
            DiscoverySecurityManager()
        } else {
            log.info("Starting restrictive Corda security manager.")
            RestrictiveSecurityManager(permissionAdmin, conditionalPermissionAdmin)
        }.apply { start() }
    }
}