package net.corda.sandbox.internal.sandbox

import net.corda.sandbox.SandboxException
import net.corda.sandbox.CpkSandbox
import org.osgi.framework.Bundle

/** Extends [CpkSandbox] with internal methods. */
internal interface CpkSandboxInternal: CpkSandbox {
    // The CPK's CorDapp bundle.
    val cordappBundle: Bundle

    /**
     * Indicates whether the [CpkSandbox]'s CorDapp bundle contains a class named [className].
     *
     * Throws [SandboxException] if the CorDapp bundle is uninstalled.
     */
    fun cordappBundleContainsClass(className: String): Boolean
}