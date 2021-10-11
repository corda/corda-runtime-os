package net.corda.sandbox

import org.osgi.framework.Bundle

/**
 * OSGi service interface for administering sandboxes.
 */
interface SandboxAdminService {
    /** Returns the bundles that failed to uninstall as part of unloading sandbox groups. */
    fun getZombieBundles(): List<Bundle>
}