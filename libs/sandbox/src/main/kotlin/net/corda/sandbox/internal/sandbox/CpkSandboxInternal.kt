package net.corda.sandbox.internal.sandbox

import net.corda.sandbox.CpkSandbox
import org.osgi.framework.Bundle

/** Extends [CpkSandbox] with internal methods. */
internal interface CpkSandboxInternal: CpkSandbox {
    // The CPK's CorDapp bundle.
    val cordappBundle: Bundle

    /** Indicates whether the [CpkSandbox]'s CorDapp bundle contains a class named [className]. */
    fun cordappBundleContainsClass(className: String): Boolean

    /** Returns the bundle with symbolic name [bundleName] from sandbox, or null if no bundle has a matching name. */
    fun getBundle(bundleName: String): Bundle?
}