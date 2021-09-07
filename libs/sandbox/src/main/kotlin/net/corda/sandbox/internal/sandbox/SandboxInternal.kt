package net.corda.sandbox.internal.sandbox

import net.corda.sandbox.Sandbox
import net.corda.sandbox.SandboxException
import org.osgi.framework.Bundle

/** Extends [Sandbox] with internal methods. */
internal interface SandboxInternal : Sandbox {
    // The sandbox's public bundles. The public bundles are the bundles that another sandbox with visibility of this
    // sandbox can see.
    val publicBundles: Set<Bundle>

    /** Indicates whether any public or private bundle in the sandbox contains the given [bundle]. */
    fun containsBundle(bundle: Bundle): Boolean

    /** Indicates whether any public or private bundle in the sandbox contains the given [klass]. */
    fun containsClass(klass: Class<*>): Boolean

    /** Indicates whether this sandbox has visibility of [otherSandbox]. */
    fun hasVisibility(otherSandbox: Sandbox): Boolean

    /** Grants this sandbox visibility of [otherSandbox]. */
    fun grantVisibility(otherSandbox: Sandbox)

    /** Grants this sandbox visibility of [otherSandboxes]. */
    fun grantVisibility(otherSandboxes: Collection<Sandbox>)
}