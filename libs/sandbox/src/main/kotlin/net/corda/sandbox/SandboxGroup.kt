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
     * one), and returns the [Class] with [className] from the CorDapp bundle of that sandbox.
     *
     * Throws [SandboxException] if there is no [Sandbox] with the given [cpkIdentifier]. If a matching sandbox is
     * found, throws [SandboxException] if the sandbox does not have a CorDapp bundle, the CorDapp bundle is
     * uninstalled, or the class is not found in the sandbox.
     */
    fun loadClassFromCordappBundle(cpkIdentifier: Cpk.Identifier, className: String): Class<*>

    /**
     * Attempts to load the [Class] with [className] from the CorDapp bundle of each sandbox in the sandbox group in
     * turn. Casts the first match to type [T] and returns it.
     *
     * Throws [SandboxException] if the sandbox's CorDapp bundle is uninstalled, or does not contain the named class.
     */
    fun <T : Any> loadClassFromCordappBundle(className: String, type: Class<T>): Class<out T>

    /**
     * Returns number of times class [className] appears in the CorDapp bundles of the sandbox group's sandboxes.
     *
     * Throws [SandboxException] if the sandbox does not have a CorDapp bundle, or the CorDapp bundle is uninstalled.
     */
    fun cordappClassCount(className: String): Int

    /** Returns the [KryoClassTag] for a given [klass]. */
    fun getKryoClassTag(klass: Class<*>): KryoClassTag

    /** Returns the [AMQPClassTag] for a given [klass]. */
    fun getAMQPClassTag(klass: Class<*>): AMQPClassTag

    /** Returns the [Class] identified by the [className] and the [classTag]. */
    fun getClass(className: String, classTag: ClassTag): Class<*>
}