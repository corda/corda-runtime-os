package net.corda.sandbox.internal

import net.corda.sandbox.SandboxGroup
import net.corda.sandbox.internal.sandbox.CpkSandbox

/** Extends [SandboxGroup] with internal methods. */
internal interface SandboxGroupInternal : SandboxGroup {
    /** The sandboxes in the group. */
    val sandboxes: Collection<CpkSandbox>
}