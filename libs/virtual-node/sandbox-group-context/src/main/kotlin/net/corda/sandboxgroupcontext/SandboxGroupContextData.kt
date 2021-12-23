package net.corda.sandboxgroupcontext

import net.corda.sandbox.SandboxGroup

/**
 * The absolute bare data for the [SandboxGroupContext].
 *
 * This is in its own interface simply because both [SandboxGroupContext]
 * and [MutableSandboxGroupContext] need to return it, but we don't want
 * [MutableSandboxGroupContext] to inherit from [SandboxGroupContext] thus
 * preventing users from accidentally calling [java.lang.AutoCloseable.close]
 * during [SandboxGroupContextInitializer].
 */
interface SandboxGroupContextData {
    val virtualNodeContext: VirtualNodeContext
    val sandboxGroup: SandboxGroup
}
