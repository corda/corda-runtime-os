package net.corda.securitymanager.internal

import org.osgi.service.component.annotations.Component
import java.security.Permission

// TODO - CORE-2828: Update [DiscoverySecurityManager] to write out updated permissions file.

/**
 * A [CordaSecurityManager] that grants sandbox code all permissions.
 *
 * While active, for any permission check that would fail under the [RestrictiveSecurityManager], it writes out the
 * corresponding permission to an updated permissions file.
 *
 * This security manager is not secure in production.
 */
@Component(service = [DiscoverySecurityManager::class])
class DiscoverySecurityManager : CordaSecurityManager, SecurityManager() {
    override fun start() {
        System.setSecurityManager(this)
    }

    override fun stop() = Unit
    override fun grantPermission(filter: String, perms: Collection<Permission>) = Unit
}