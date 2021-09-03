package net.corda.sandbox.internal

import net.corda.sandbox.Sandbox
import net.corda.sandbox.SandboxService
import org.osgi.framework.Bundle

/**
 * Extends [SandboxService] with methods that are used within the sandboxing hooks.
 */
internal interface SandboxServiceInternal : SandboxService {
    /** Returns the [SandboxInternal] containing the given [bundle], or null if no match. */
    fun getSandbox(bundle: Bundle): SandboxInternal?

    /**
     * Checks whether the [sandbox] is one of the platform sandboxes (i.e. the sandboxes containing the platform's
     * bundles).
     */
    fun isPlatformSandbox(sandbox: Sandbox): Boolean

    /**
     * Returns true if the [lookingBundle]'s sandbox and the [lookedAtBundle]'s sandbox are both null, if both the
     * [lookingBundle] and the [lookedAtBundle] are in the same sandbox, or if [lookedAtBundle] is a CorDapp bundle
     * in a sandbox that [lookingBundle]'s sandbox has visibility of.
     */
    fun hasVisibility(lookingBundle: Bundle, lookedAtBundle: Bundle): Boolean
}