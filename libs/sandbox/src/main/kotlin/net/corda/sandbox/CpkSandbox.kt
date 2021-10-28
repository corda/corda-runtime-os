package net.corda.sandbox

import net.corda.packaging.CPK

/** A container for isolating a set of bundles created from a CPK. */
interface CpkSandbox: Sandbox {
    val cpk: CPK

    /**
     * Loads the [Class] with [className] from the sandbox's main bundle. Returns null if the bundle does not
     * contain the named class.
     *
     * Throws [SandboxException] if the main bundle does not contain the named class, or is uninstalled.
     */
    fun loadClassFromMainBundle(className: String): Class<*>
}