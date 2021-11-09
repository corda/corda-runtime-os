package net.corda.sandbox.internal.sandbox

import net.corda.packaging.CPK
import org.osgi.framework.Bundle

/** A container for isolating a set of bundles created from a CPK. */
internal interface CpkSandbox : Sandbox {
    /** The CPK the sandbox was created from. */
    val cpk: CPK

    /** The CPK's main bundle. */
    val mainBundle: Bundle

    /**
     * Loads the [Class] with [className] from the sandbox's main bundle. Returns null if the bundle does not
     * contain the named class.
     *
     * Throws `SandboxException` if the main bundle does not contain the named class, or is uninstalled.
     */
    fun loadClassFromMainBundle(className: String): Class<*>
}