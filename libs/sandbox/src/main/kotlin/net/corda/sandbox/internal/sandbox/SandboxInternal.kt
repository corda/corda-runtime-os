package net.corda.sandbox.internal.sandbox

import net.corda.sandbox.Sandbox
import net.corda.sandbox.SandboxException
import org.osgi.framework.Bundle

/** Extends [Sandbox] with internal methods. */
internal interface SandboxInternal : Sandbox {
    /** Indicates whether this sandbox contains the given [bundle]. */
    fun containsBundle(bundle: Bundle): Boolean

    /** Indicates whether this sandbox contains the given [klass]. */
    fun containsClass(klass: Class<*>): Boolean

    /**
     * Returns the [Bundle] the class is loaded from.
     *
     * Throws [SandboxException] if the class is not found in any bundle in the sandbox.
     */
    fun getBundle(klass: Class<*>): Bundle

    /** Indicates whether this sandbox has visibility of [otherSandbox]. */
    fun hasVisibility(otherSandbox: Sandbox): Boolean

    /** Grants this sandbox visibility of [otherSandbox]. */
    fun grantVisibility(otherSandbox: Sandbox)

    /** Grants this sandbox visibility of [otherSandboxes]. */
    fun grantVisibility(otherSandboxes: Collection<Sandbox>)
}