package net.corda.sandbox.internal.sandbox

import net.corda.sandbox.Sandbox
import net.corda.sandbox.SandboxException
import org.osgi.framework.Bundle

/** Extends [Sandbox] with internal methods. */
internal interface SandboxInternal : Sandbox {
    // The sandbox's public bundles. The public bundles are the bundles that another sandbox with visibility of this
    // sandbox can see.
    val publicBundles: Set<Bundle>

    /** Indicates whether any public or private bundle in the sandbox contains the given [bundle]. */
    fun containsBundle(bundle: Bundle): Boolean

    /** Indicates whether any public or private bundle in the sandbox contains the given [klass]. */
    fun containsClass(klass: Class<*>): Boolean

    /** Indicates whether this sandbox has visibility of [otherSandbox]. */
    fun hasVisibility(otherSandbox: Sandbox): Boolean

    /** Grants this sandbox visibility of [otherSandbox]. */
    fun grantVisibility(otherSandbox: Sandbox)

    /** Grants this sandbox visibility of the [otherSandboxes]. */
    fun grantVisibility(otherSandboxes: List<Sandbox>)

    /** Returns the bundle with symbolic name [bundleName] from sandbox, or null if no bundle has a matching name. */
    fun getBundle(bundleName: String): Bundle?

    /**
     * Loads the class with [className] from the bundle in the sandbox identified by [bundleName]. Returns null if no
     * bundle has a matching name, or if the bundle does not contain the named class.
     *
     * Throws [SandboxException] if the bundle is uninstalled.
     */
    fun loadClass(className: String, bundleName: String): Class<*>?

    /**
     * Uninstalls all the sandbox's bundles.
     *
     * Returns a list of bundles that could not be uninstalled, with their cause of failure.
     */
    fun unload(): List<String>
}