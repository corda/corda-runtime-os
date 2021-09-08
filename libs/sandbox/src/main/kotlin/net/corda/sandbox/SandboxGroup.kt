package net.corda.sandbox

import net.corda.packaging.Cpk

/** An interface to a group of sandboxes with visibility of one another. */
interface SandboxGroup {
    /** The sandboxes in the group. */
    val sandboxes: Collection<CpkSandbox>

    /**
     * Returns the [CpkSandbox] out of [sandboxes] with the given [Cpk.Identifier]. There is guaranteed to be at most
     * one. Returns null if no sandbox with the given CPK identifier exists.
     */
    fun getSandbox(cpkIdentifier: Cpk.Identifier): CpkSandbox?

    /**
     * Finds the [CpkSandbox] out of [sandboxes] with the given [Cpk.Identifier] (there is guaranteed to be at most
     * one), and loads the [Class] with [className] from the CorDapp bundle of that sandbox. Returns null if there is
     * no sandbox with the given CPK identifier, or if this sandbox does not contain the named class.
     *
     * Throws [SandboxException] if the CorDapp bundle of the sandbox with the given CPK identifier is uninstalled.
     */
    fun loadClassFromCordappBundle(cpkIdentifier: Cpk.Identifier, className: String): Class<*>?

    /**
     * Attempts to load the [Class] with [className] from the CorDapp bundle of each sandbox in the sandbox group in
     * turn. Casts the first match to type [T] and returns it. Returns null if no sandbox contains the named class.
     *
     * Throws [SandboxException] if any of the sandboxes' CorDapp bundles are uninstalled. Throws [ClassCastException]
     * if the named class is not of the correct type.
     */
    fun <T : Any> loadClassFromCordappBundle(className: String, type: Class<T>): Class<out T>?

    /**
     * Returns number of times class [className] appears in the CorDapp bundles of the sandbox group's sandboxes.
     *
     * Throws [SandboxException] if any of the sandboxes' CorDapp bundles are uninstalled.
     */
    fun cordappClassCount(className: String): Int

    /**
     * Returns the [StaticTag] for a given [klass]. Returns null if the class is not contained in any bundle, or is
     * contained in a bundle that is not contained in any sandbox in the group or in the platform sandbox.
     */
    fun getStaticTag(klass: Class<*>): StaticTag?

    /**
     * Returns the [EvolvableTag] for a given [klass]. Returns null if the class is not contained in any bundle, or is
     * contained in a bundle that is not contained in any sandbox in the group or in the platform sandbox.
     */
    fun getEvolvableTag(klass: Class<*>): EvolvableTag?

    /**
     * Returns the [Class] identified by the [className] and the [classTag]. Returns null if there is no sandbox
     * matching the tag, or if the class is not contained in the matching sandbox or in the platform sandbox.
     *
     * Throws [SandboxException] if the [ClassTag] implementation is not recognised.
     */
    fun getClass(className: String, classTag: ClassTag): Class<*>?
}