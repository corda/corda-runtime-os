package net.corda.sandbox

/** An interface to a group of sandboxes with visibility of one another. */
interface SandboxGroup {
    /**
     * Attempts to load the [Class] with [className] from the main bundle of each sandbox in the sandbox group in
     * turn. Casts the first match to type [T] and returns it.
     *
     * Throws [SandboxException] if no sandbox contains the named class, if any of the sandboxes' main bundles are
     * uninstalled, or if the named class is not of the correct type.
     */
    fun <T : Any> loadClassFromMainBundles(className: String, type: Class<T>): Class<out T>

    /**
     * Returns the serialised static tag for a given [klass].
     *
     * Throws [SandboxException] if the class is not loaded from any bundle, or is contained in a bundle that is not
     * contained in any sandbox in the group or in a public sandbox.
     */
    fun getStaticTag(klass: Class<*>): String

    /**
     * Returns the serialised evolvable tag for a given [klass].
     *
     * Throws [SandboxException] if the class is not loaded from any bundle, or is contained in a bundle that is not
     * contained in any sandbox in the group or in a public sandbox.
     */
    fun getEvolvableTag(klass: Class<*>): String

    /**
     * Returns the [Class] identified by the [className] and the [serialisedClassTag].
     *
     * Throws [SandboxException] if there is no sandbox matching the tag, if the class is not contained in the matching
     * sandbox or in a public sandbox, or if the class tag cannot be parsed.
     */
    fun getClass(className: String, serialisedClassTag: String): Class<*>
}