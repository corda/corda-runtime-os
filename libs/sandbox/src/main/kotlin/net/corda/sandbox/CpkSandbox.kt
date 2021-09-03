package net.corda.sandbox

import net.corda.packaging.Cpk

/** A container for isolating a set of bundles created from a CPK. */
interface CpkSandbox: Sandbox {
    val cpk: Cpk.Expanded

    /**
     * Loads the [Class] with [className] from the sandbox's CorDapp bundle.
     *
     * Throws [SandboxException] if the CorDapp bundle is uninstalled, or the class is not found in the sandbox.
     */
    fun loadClass(className: String): Class<*>
}