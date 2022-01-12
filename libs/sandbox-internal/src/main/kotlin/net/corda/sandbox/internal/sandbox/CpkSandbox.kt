package net.corda.sandbox.internal.sandbox

import net.corda.packaging.CPK
import org.osgi.framework.Bundle

/** A container for isolating a set of bundles created from a CPK. */
internal interface CpkSandbox : Sandbox {
    /** The CPK the sandbox was created from. */
    val cpk: CPK

    /** The CPK's main bundle. */
    val mainBundle: Bundle

    /** The CPK's private bundles. It should contain cpk's libraries or private implementations. */
    val privateBundles: Set<Bundle>

    /**
     * Loads the [Class] with [className] from the sandbox's main bundle.
     *
     * @throws `SandboxException` if the main bundle does not contain the named class,
     * or the class belongs to a private package, or the bundle is uninstalled.
     */
    fun loadClassFromMainBundle(className: String): Class<*>
}
