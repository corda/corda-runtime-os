package net.corda.securitymanager.internal

import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component
import org.osgi.service.permissionadmin.PermissionInfo
import java.security.Permission

/**
 * A [CordaSecurityManager] that grants sandbox code all permissions.
 *
 * While active, it logs any permission checks for which any class on the execution stack is from a bundle whose
 * location matches one of the [prefixes].
 *
 * This security manager is not secure for production use.
 */
@Component(service = [DiscoverySecurityManager::class])
class DiscoverySecurityManager(
    prefixes: Collection<String>,
    private val bundleUtils: BundleUtils
) : CordaSecurityManager, SecurityManager() {
    companion object {
        private val log = contextLogger()
        private val discoverySecurityManagerClass = DiscoverySecurityManager::class.java
    }

    // We log permissions for bundles whose location matches one of these prefixes.
    private val prefixes: Set<String>

    // The security manager that this `DiscoverySecurityManager` replaces, if any.
    private var previousSecurityManager: SecurityManager?

    init {
        this.prefixes = prefixes.toSet()
        val previousSecurityManager = System.getSecurityManager()
        System.setSecurityManager(this)
        this.previousSecurityManager = previousSecurityManager
    }

    /** Restores the security manager that was replaced by this [DiscoverySecurityManager]. */
    override fun stop() {
        if (System.getSecurityManager() !== this) {
            log.warn("The DiscoverySecurityManager has already been replaced by another security manager.")
        } else {
            System.setSecurityManager(previousSecurityManager)
        }
    }

    override fun grantPermissions(filter: String, perms: Collection<Permission>) = Unit
    override fun denyPermissions(filter: String, perms: Collection<Permission>) = Unit

    /** Logs the [perm] if a class on the stack is from a bundle whose location matches one of the [prefixes]. */
    override fun checkPermission(perm: Permission) {
        // This check ensures that any calls in `checkPermission` that themselves trigger a permission check don't
        // cause an infinite recursion.
        if (isRecursiveDiscoverySecurityManagerCall()) return

        classContext.forEach { klass ->
            if (matchesAnyPrefix(klass)) {
                // We log the permission as a `PermissionInfo` so that the permission is printed using the encoding in
                // which it needs to be added to the CorDapp's `AdditionalPermissions` file.
                val permissionInfo = PermissionInfo(perm::class.java.name, perm.name, perm.actions)
                log.info("$klass requested permission $permissionInfo.")
                // Once the permission is logged, we can return early.
                return
            }
        }
    }

    /** Checks if the call stack contains earlier calls to [DiscoverySecurityManager], indicating a recursive call. */
    private fun isRecursiveDiscoverySecurityManagerCall() = classContext
        // We skip over the current set of calls to `DiscoverySecurityManager`.
        .dropWhile { klass -> klass == discoverySecurityManagerClass }
        .any { klass -> klass == discoverySecurityManagerClass }

    /** Checks if the location of the bundle containing the [klass] matches any of the [prefixes]. */
    private fun matchesAnyPrefix(klass: Class<*>): Boolean {
        val classBundleLocation = bundleUtils.getBundleLocation(klass) ?: return false
        return prefixes.any { filter -> classBundleLocation.startsWith(filter) }
    }
}