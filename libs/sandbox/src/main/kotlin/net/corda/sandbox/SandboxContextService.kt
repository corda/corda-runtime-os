package net.corda.sandbox

import net.corda.packaging.CPK
import org.osgi.framework.Bundle

/**
 * OSGi service interface for retrieving context on the current sandbox.
 */
interface SandboxContextService {
    /**
     * Returns the [Sandbox] lowest in the stack of calls to this function, or null if no sandbox is on the stack.
     *
     * A [SandboxException] is thrown if the sandbox bundle's location is not formatted correctly, the ID is not a
     * valid UUID, or there is no known sandbox with the given ID.
     */
    fun getCallingSandbox(): Sandbox?

    /**
     * Finds the [Sandbox] lowest in the stack of calls to this function, and returns the [SandboxGroup] that contains
     * it. Returns null if no sandbox is on the stack.
     *
     * A [SandboxException] is thrown if the sandbox bundle's location is not formatted correctly, the ID is not a
     * valid UUID, there is no known sandbox with the given ID, or there is no sandbox group containing the sandbox
     * with the given ID.
     */
    fun getCallingSandboxGroup(): SandboxGroup?

    /**
     * Finds the [Sandbox] lowest in the stack of calls to this function, and returns the [CPK.Identifier] of the
     * [CPK] it was created from. Returns null if no sandbox is on the stack, or if the sandbox has no source CPK.
     *
     * A [SandboxException] is thrown if the sandbox bundle's location is not formatted correctly, the ID is not a
     * valid UUID, or there is no known sandbox with the given ID.
     */
    fun getCallingCpk(): CPK.Identifier?

    /** Returns the [Sandbox] containing the given [bundle], or null if no match. */
    fun getSandbox(bundle: Bundle): Sandbox?

    /**
     * Returns true if the [lookingBundle]'s sandbox and the [lookedAtBundle]'s sandbox are both null, if both the
     * [lookingBundle] and the [lookedAtBundle] are in the same sandbox, or if [lookedAtBundle] is a CorDapp bundle
     * in a sandbox that [lookingBundle]'s sandbox has visibility of.
     */
    fun hasVisibility(lookingBundle: Bundle, lookedAtBundle: Bundle): Boolean
}