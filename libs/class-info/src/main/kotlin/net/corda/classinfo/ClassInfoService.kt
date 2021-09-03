package net.corda.classinfo

import net.corda.sandbox.ClassInfo
import net.corda.sandbox.SandboxException

/** Given a class or class name, provides [ClassInfo] concerning that class. */
interface ClassInfoService {
    /**
     * Returns the [ClassInfo] for the given [klass].
     *
     * A [ClassInfoException] is thrown if the class is not in a sandbox, or is not found in any bundle the sandbox
     * has visibility of.
     */
    fun getClassInfo(klass: Class<*>): ClassInfo

    /**
     * Returns the [ClassInfo] for the class with the given [className]. If the className occurs more than once in
     * the sandboxGroup then the first one found is returned.
     *
     * A [SandboxException] is thrown if [className] is not found in the sandboxGroup.
     */
    fun getClassInfo(className: String): ClassInfo
}