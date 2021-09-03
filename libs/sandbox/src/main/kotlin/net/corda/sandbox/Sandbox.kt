package net.corda.sandbox

import net.corda.packaging.Cpk
import java.util.UUID

/** A container for isolating a set of bundles. */
interface Sandbox {
    // The sandbox's unique identifier.
    val id: UUID

    // The CPK the sandbox was created for, or null for a platform sandbox.
    val cpk: Cpk.Expanded?

    /**
     * Loads the [Class] with [className] from the sandbox's CorDapp bundle.
     *
     * Throws [SandboxException] if the sandbox does not have a CorDapp bundle, the CorDapp bundle is uninstalled, or
     * the class is not found in the sandbox.
     */
    fun loadClass(className: String): Class<*>
}