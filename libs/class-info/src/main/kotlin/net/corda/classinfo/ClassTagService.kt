package net.corda.classinfo

import net.corda.sandbox.SandboxException

/** Given a class or class name, provides [ClassTag] concerning that class. */
interface ClassTagService {
    /**
     * Returns the [ClassTag] for the given [klass].
     *
     * A [ClassTagException] is thrown if the class is not in a sandbox, or is not found in any bundle the sandbox
     * has visibility of.
     */
    fun getClassTag(klass: Class<*>): String

    /**
     * Returns the [ClassTag] for the class with the given [className]. If the className occurs more than once in
     * the sandboxGroup then the first one found is returned.
     *
     * A [SandboxException] is thrown if [className] is not found in the sandboxGroup.
     */
    fun getClassTag(className: String): String
}