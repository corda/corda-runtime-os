package net.corda.sandbox.internal

import net.corda.sandbox.SandboxContextService
import net.corda.sandbox.SandboxCreationService
import net.corda.sandbox.internal.sandbox.SandboxInternal
import org.osgi.framework.Bundle

/** Extends [SandboxCreationService] and [SandboxContextService] with internal methods. */
internal interface SandboxServiceInternal : SandboxCreationService, SandboxContextService {
    /** Returns the [SandboxInternal] containing the given [bundle], or null if no match. */
    fun getSandbox(bundle: Bundle): SandboxInternal?

    /**
     * Returns true if the [lookingBundle]'s sandbox and the [lookedAtBundle]'s sandbox are both null, if both the
     * [lookingBundle] and the [lookedAtBundle] are in the same sandbox, or if [lookedAtBundle] is a CorDapp bundle
     * in a sandbox that [lookingBundle]'s sandbox has visibility of.
     */
    fun hasVisibility(lookingBundle: Bundle, lookedAtBundle: Bundle): Boolean
}