package net.corda.sandbox

import net.corda.packaging.Cpk

/** An interface to a group of sandboxes with visibility of one another. */
interface SandboxGroup {
    /** The sandboxes in the group. */
    val sandboxes: Collection<Sandbox>

    /**
     * Returns the [Sandbox] with the given [Cpk.Identifier].
     *
     * Throws a [SandboxException] if none of the [sandboxes] have the given [cpkIdentifier].
     */
    fun getSandbox(cpkIdentifier: Cpk.Identifier): Sandbox

    /**
     * Loads the [Class] with [className] from the sandbox in [sandboxes] that has the given [cpkIdentifier].
     *
     * Throws [SandboxException] if there is no [Sandbox] with the given [cpkIdentifier]. If a matching sandbox is
     * found, throws [SandboxException] if the sandbox does not have a CorDapp bundle, the CorDapp bundle is
     * uninstalled, or the class is not found in the sandbox.
     */
    fun loadClass(cpkIdentifier: Cpk.Identifier, className: String): Class<*>

    /**
     * Loads the [Class] with [className] from the sandbox group and casts it to type T.
     * It is assumed the class will be unique within the group.
     *
     * Throws [SandboxException] if the sandbox does not have a CorDapp bundle, the CorDapp bundle is
     * uninstalled, or the class is not found in the sandbox.
     */
    fun <T : Any> loadClass(className: String, type: Class<T>): Class<out T>

    /**
     * Returns number of times class [classname] appears in the Sandbox group
     *
     * Throws [SandboxException] if the sandbox does not have a CorDapp bundle, the CorDapp bundle is
     * uninstalled.
     */
    fun classCount(className: String): Int
}