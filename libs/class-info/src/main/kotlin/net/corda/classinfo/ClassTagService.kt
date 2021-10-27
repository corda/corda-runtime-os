package net.corda.classinfo

/** Given a class or class name, provides [ClassTag] concerning that class. */
interface ClassTagService {
    /**
     * Returns the [ClassTag] for the given [klass].
     *
     * A [ClassTagException] is thrown if the class is not in a sandbox, or is not found in any bundle the sandbox
     * has visibility of.
     */
    fun getClassTag(klass: Class<*>, isStaticTag: Boolean): String
}