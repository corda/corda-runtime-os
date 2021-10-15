package net.corda.securitymanager.internal

import net.corda.securitymanager.SecurityManagerService
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.security.AllPermission
import java.security.CodeSource
import java.security.Permissions
import java.security.Policy

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

        // A security policy granting all permissions.
        val allPermissionsPolicy = object : Policy() {
            override fun getPermissions(codesource: CodeSource) = Permissions().apply { add(AllPermission()) }
        }
    }

    // The currently-running Corda security manager.
    private var securityManager: CordaSecurityManager? = null

    @Suppress("unused")
    override fun start(isDiscoveryMode: Boolean) {
        if (securityManager != null) stop()

        securityManager = if (isDiscoveryMode) {
            log.info("Starting discovery Corda security manager. This is not secure in production.")
            discoverySecurityManager
        } else {
            log.info("Starting restrictive Corda security manager.")
            restrictiveSecurityManager
        }

        securityManager?.start()
    }

    /** Stops the current Corda security manager, reverting to a security manager granting all permissions. */
    private fun stop() {
        securityManager?.stop()
        securityManager = null
        Policy.setPolicy(allPermissionsPolicy)
        System.setSecurityManager(SecurityManager())
    }
}