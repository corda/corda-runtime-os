package net.corda.sandbox.internal

import net.corda.sandbox.Sandbox
import net.corda.sandbox.SandboxException
import org.osgi.framework.Bundle
import java.net.URI
import java.util.UUID

/**
 * Extends [Sandbox] with methods that are used by [SandboxServiceInternal].
 */
internal interface SandboxInternal : Sandbox {
    companion object {
        /** Generates a unique location to use when installing a bundle into sandbox [id] from [uri]. */
        fun getLocation(id: UUID, uri: URI) = SandboxLocation(id, uri)
    }

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

    /** Returns true if the [bundle] is this sandbox's CorDapp bundle. */
    fun isCordappBundle(bundle: Bundle): Boolean

    /** Indicates whether this sandbox has visibility of [otherSandbox]. */
    fun hasVisibility(otherSandbox: Sandbox): Boolean

    /** Grants this sandbox visibility of [otherSandbox]. */
    fun grantVisibility(otherSandbox: Sandbox)

    /** Grants this sandbox visibility of [otherSandboxes]. */
    fun grantVisibility(otherSandboxes: Collection<Sandbox>)

    /**
     * Removes this sandbox's visibility of [otherSandbox].
     * @return true if visibility was revoked, otherwise false.
     */
    fun revokeVisibility(otherSandbox: Sandbox): Boolean

    /** Uninstalls all the sandbox's bundles. */
    fun uninstallBundles()
}