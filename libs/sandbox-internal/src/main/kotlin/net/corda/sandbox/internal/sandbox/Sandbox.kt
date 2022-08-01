package net.corda.sandbox.internal.sandbox

import net.corda.sandbox.SandboxException
import org.osgi.framework.Bundle
import java.util.UUID

/** A container for isolating a set of bundles. */
internal interface Sandbox {
    /** The sandbox's unique identifier. */
    val id: UUID

    /** The sandbox's public bundles. The public bundles are the bundles that another sandbox with visibility of this
     *  sandbox can see. */
    val publicBundles: Set<Bundle>

    /** The sandbox's private bundles. The private bundles are the bundles that another sandbox with visibility of this
     *  sandbox can't see. */
    val privateBundles: Set<Bundle>

    /** Indicates whether any public or private bundle in the sandbox contains the given [bundle]. */
    fun containsBundle(bundle: Bundle): Boolean

    /** Indicates whether this sandbox has visibility of [otherSandbox]. */
    fun hasVisibility(otherSandbox: Sandbox): Boolean

    /** Grants this sandbox visibility of the [otherSandboxes]. */
    fun grantVisibility(otherSandboxes: Collection<Sandbox>)

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
     * Returns at most two lists of bundles.
     * The bundles for `true` are no longer installed,
     * whereas the bundles for `false` are still installed.
     */
    fun unload(): Map<Boolean, List<Bundle>>
}
