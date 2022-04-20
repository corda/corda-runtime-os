package net.corda.sandboxgroupcontext.service.impl

import net.corda.sandbox.SandboxGroup
import net.corda.sandboxgroupcontext.MutableSandboxGroupContext
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.v5.base.util.loggerFor

/**
 * [MutableSandboxGroupContext] / [SandboxGroupContext] wrapped so that we set [close] now that the user has
 * returned it as part of their [SandboxGroupContextInitializer]
 *
 * We return an instance of this object of type [SandboxGroupContext] to the user once [getOrCreate] is complete.
 */
interface CloseableSandboxGroupContext: MutableSandboxGroupContext, AutoCloseable

internal class CloseableSandboxGroupContextImpl(
    private val sandboxGroupContext: SandboxGroupContextImpl,
    private val closeable: AutoCloseable
) : CloseableSandboxGroupContext {
    private companion object {
        private val logger = loggerFor<CloseableSandboxGroupContext>()
    }

    override fun <T : Any> put(key: String, value: T) =
        sandboxGroupContext.put(key, value)

    override val virtualNodeContext: VirtualNodeContext
        get() = sandboxGroupContext.virtualNodeContext

    override val sandboxGroup: SandboxGroup
        get() = sandboxGroupContext.sandboxGroup

    override fun <T : Any> get(key: String, valueType: Class<out T>): T? = sandboxGroupContext.get(key, valueType)

    override fun close() {
        try {
            closeable.close()
        } catch(e: Exception) {
            logger.warn("Failed to close SandboxGroupContext", e)
        }
    }
}