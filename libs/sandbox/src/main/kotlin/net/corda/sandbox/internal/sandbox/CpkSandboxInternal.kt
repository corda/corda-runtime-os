package net.corda.sandbox.internal.sandbox

import net.corda.sandbox.CpkSandbox
import org.osgi.framework.Bundle

/** Extends [CpkSandbox] with internal methods. */
internal interface CpkSandboxInternal: CpkSandbox {
    /** Indicates whether the [CpkSandbox]'s CorDapp bundle contains a class named [className]. */
    fun cordappBundleContainsClass(className: String): Boolean

    /** Returns true if the [bundle] is this sandbox's CorDapp bundle. */
    fun isCordappBundle(bundle: Bundle): Boolean
}