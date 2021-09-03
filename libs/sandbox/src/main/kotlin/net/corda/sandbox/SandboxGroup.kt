package net.corda.sandbox

import net.corda.packaging.Cpk

/** An interface to a group of sandboxes with visibility of one another. */
interface SandboxGroup {
    /** The sandboxes in the group. */
    val sandboxes: Collection<CpkSandbox>

    /**
     * Returns the [CpkSandbox] out of [sandboxes] with the given [Cpk.Identifier]. There is guaranteed to be at most
     * one.
     *
     * Throws a [SandboxException] if none of the [sandboxes] have the given [cpkIdentifier].
     */
    fun getSandbox(cpkIdentifier: Cpk.Identifier): CpkSandbox

    /**
     * Finds the [CpkSandbox] out of [sandboxes] with the given [Cpk.Identifier] (there is guaranteed to be at most
     * one), and returns the [Class] with [className] from that sandbox.
     *
     * Throws [SandboxException] if there is no [Sandbox] with the given [cpkIdentifier]. If a matching sandbox is
     * found, throws [SandboxException] if the sandbox does not have a CorDapp bundle, the CorDapp bundle is
     * uninstalled, or the class is not found in the sandbox.
     */
    fun loadClass(cpkIdentifier: Cpk.Identifier, className: String): Class<*>

    /**
     * Loads the [Class] with [className] from the sandbox group and casts it to type T. It is assumed that the class
     * is only contained in one [Sandbox]'s CorDapp bundle across the group.
     *
     * Throws [SandboxException] if the sandbox's CorDapp bundle is uninstalled, or does not contain the named class.
     */
    fun <T : Any> loadClass(className: String, type: Class<T>): Class<out T>

    /**
     * Returns number of times class [className] appears in the sandbox group.
     *
     * Throws [SandboxException] if the sandbox does not have a CorDapp bundle, the CorDapp bundle is
     * uninstalled.
     */
    fun classCount(className: String): Int
}