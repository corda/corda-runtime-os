package net.corda.sandbox.internal.sandbox

import net.corda.sandbox.CpkSandbox
import org.osgi.framework.Bundle

/** Extends [CpkSandbox] with internal methods. */
internal interface CpkSandboxInternal: CpkSandbox, SandboxInternal {
    // The CPK's main bundle.
    val mainBundle: Bundle
}