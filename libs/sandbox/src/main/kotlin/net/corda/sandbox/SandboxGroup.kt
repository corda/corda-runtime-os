package net.corda.sandbox

import net.corda.packaging.Cpk

/** An interface to a group of sandboxes with visibility of one another. */
interface SandboxGroup {
    /** The sandboxes in the group. */
    val sandboxes: Collection<CpkSandbox>

    /**
     * Returns the [CpkSandbox] out of [sandboxes] with the given [Cpk.ShortIdentifier]. There is guaranteed to be at
     * most one.
     *
     * Throws [SandboxException] if no sandbox with the given CPK identifier exists.
     */
    fun getSandbox(cpkIdentifier: Cpk.ShortIdentifier): CpkSandbox

    /**
     * Finds the [CpkSandbox] out of [sandboxes] with the given [Cpk.ShortIdentifier] (there is guaranteed to be at
     * most one), and loads the [Class] with [className] from the CorDapp bundle of that sandbox.
     *
     * Throws [SandboxException] if there is no sandbox with the given CPK identifier, if this sandbox does not contain
     * the named class, or if the CorDapp bundle of the sandbox with the given CPK identifier is uninstalled.
     */
    fun loadClassFromCordappBundle(cpkIdentifier: Cpk.ShortIdentifier, className: String): Class<*>

    /**
     * Attempts to load the [Class] with [className] from the CorDapp bundle of each sandbox in the sandbox group in
     * turn. Casts the first match to type [T] and returns it.
     *
     * Throws [SandboxException] if no sandbox contains the named class, if any of the sandboxes' CorDapp bundles are
     * uninstalled, or if the named class is not of the correct type.
     */
    fun <T : Any> loadClassFromCordappBundle(className: String, type: Class<T>): Class<out T>

    /**
     * Returns number of times class [className] appears in the CorDapp bundles of the sandbox group's sandboxes.
     *
     * Throws [SandboxException] if any of the sandboxes' CorDapp bundles are uninstalled.
     */
    fun cordappClassCount(className: String): Int

    /**
     * Returns the serialised static tag for a given [klass].
     *
     * Throws [SandboxException] if the class is not loaded from any bundle, or is contained in a bundle that is not
     * contained in any sandbox in the group or in the platform sandbox.
     */
    fun getStaticTag(klass: Class<*>): String

    /**
     * Returns the serialised evolvable tag for a given [klass].
     *
     * Throws [SandboxException] if the class is not loaded from any bundle, or is contained in a bundle that is not
     * contained in any sandbox in the group or in the platform sandbox.
     */
    fun getEvolvableTag(klass: Class<*>): String

    /**
     * Returns the [Class] identified by the [className] and the [serialisedClassTag].
     *
     * Throws [SandboxException] if there is no sandbox matching the tag, if the class is not contained in the matching
     * sandbox or in the platform sandbox, or if the class tag cannot be parsed.
     */
    fun getClass(className: String, serialisedClassTag: String): Class<*>
}