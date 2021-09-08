package net.corda.sandbox

import net.corda.packaging.Cpk

/** A container for isolating a set of bundles created from a CPK. */
interface CpkSandbox: Sandbox {
    val cpk: Cpk.Expanded

    /**
     * Loads the [Class] with [className] from the sandbox's CorDapp bundle. Returns null if the bundle does not
     * contain the named class.
     *
     * Throws [SandboxException] if the CorDapp bundle is uninstalled.
     */
    fun loadClassFromCordappBundle(className: String): Class<*>?
}