package net.corda.sandbox

import org.osgi.framework.Bundle

/** OSGi service interface for retrieving context on the current sandbox. */
interface SandboxContextService {
    /**
     * Returns the [ClassInfo] for the given [klass].
     *
     * A [SandboxException] is thrown if the class is not in a sandbox, or is not found in any bundle the sandbox has
     * visibility of.
     */
    fun getClassInfo(klass: Class<*>): ClassInfo

    /**
     * Returns the [ClassInfo] for the class with the given [className]. If the className occurs more than once in
     * the sandboxGroup then the first one found is returned.
     *
     * A [SandboxException] is thrown if [className] is not found in the sandboxGroup.
     */
    fun getClassInfo(className: String): ClassInfo

    /**
     * Finds the sandbox lowest in the stack of calls to this function, and returns the [SandboxGroup] that contains
     * it. Returns null if no sandbox is on the stack.
     *
     * A [SandboxException] is thrown if the sandbox bundle's location is not formatted correctly, the ID is not a
     * valid UUID, there is no known sandbox with the given ID, or there is no sandbox group containing the sandbox
     * with the given ID.
     */
    fun getCallingSandboxGroup(): SandboxGroup?

    /** Indicates whether the [bundle] is in a sandbox. */
    fun isSandboxed(bundle: Bundle): Boolean

    /** Indicates whether [bundleOne] and [bundleTwo] are in the same sandbox. */
    fun areInSameSandbox(bundleOne: Bundle, bundleTwo: Bundle): Boolean

    /**
     * Returns true if the [lookingBundle]'s sandbox and the [lookedAtBundle]'s sandbox are both null, if both the
     * [lookingBundle] and the [lookedAtBundle] are in the same sandbox, or if [lookedAtBundle] is the main bundle
     * in a sandbox that [lookingBundle]'s sandbox has visibility of.
     */
    fun hasVisibility(lookingBundle: Bundle, lookedAtBundle: Bundle): Boolean
}