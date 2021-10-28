package net.corda.sandbox.internal.sandbox

import net.corda.sandbox.SandboxException
import net.corda.sandbox.CpkSandbox
import org.osgi.framework.Bundle

/** Extends [CpkSandbox] with internal methods. */
internal interface CpkSandboxInternal: CpkSandbox, SandboxInternal {
    // The CPK's main bundle.
    val mainBundle: Bundle

    /**
     * Indicates whether the [CpkSandbox]'s main bundle contains a class named [className].
     *
     * Throws [SandboxException] if the main bundle is uninstalled.
     */
    fun mainBundleContainsClass(className: String): Boolean
}