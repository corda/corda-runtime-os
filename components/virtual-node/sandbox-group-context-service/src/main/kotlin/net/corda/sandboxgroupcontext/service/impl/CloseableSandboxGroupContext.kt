package net.corda.sandboxgroupcontext.service.impl

import net.corda.sandbox.SandboxGroup
import net.corda.sandboxgroupcontext.MutableSandboxGroupContext
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.v5.base.util.loggerFor
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

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

    private val lock = ReentrantLock()
    private var isClosed = false

    override fun <T : Any> put(key: String, value: T) =
        sandboxGroupContext.put(key, value)

    override val virtualNodeContext: VirtualNodeContext
        get() = sandboxGroupContext.virtualNodeContext

    override val sandboxGroup: SandboxGroup
        get() = sandboxGroupContext.sandboxGroup

    override fun <T : Any> get(key: String, valueType: Class<out T>): T? = sandboxGroupContext.get(key, valueType)

    override fun close() {
        if (isClosed)
            return

        lock.withLock {
            if (!isClosed) {
                try {
                    closeable.close()
                    isClosed = true
                } catch (e: Exception) {
                    logger.warn("Failed to close SandboxGroupContext", e)
                }
            }
        }
    }
}