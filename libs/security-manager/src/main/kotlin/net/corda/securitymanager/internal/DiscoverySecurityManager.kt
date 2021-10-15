package net.corda.securitymanager.internal

// TODO - CORE-2828: Update [DiscoverySecurityManager] to write out updated permissions file.

/**
 * A [CordaSecurityManager] that grants sandbox code all permissions.
 *
 * While active, for any permission check that would fail under the [RestrictiveSecurityManager], it writes out the
 * corresponding permission to an updated permissions file.
 *
 * This security manager is not secure in production.
 */
class DiscoverySecurityManager: CordaSecurityManager, SecurityManager() {
    override fun start() {
        System.setSecurityManager(this)
    }
}