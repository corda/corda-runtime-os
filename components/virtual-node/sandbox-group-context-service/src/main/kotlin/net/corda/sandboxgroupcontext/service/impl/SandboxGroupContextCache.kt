package net.corda.sandboxgroupcontext.service.impl

import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.v5.base.util.loggerFor
import java.util.concurrent.ConcurrentHashMap

class SandboxGroupContextCache {
    private companion object {
        private val logger = loggerFor<SandboxGroupContextCache>()
    }
    private val contexts = ConcurrentHashMap<VirtualNodeContext, CloseableSandboxGroupContext>()

    fun remove(virtualNodeContext: VirtualNodeContext) {
        contexts.remove(virtualNodeContext)?.close()
    }

    fun get(
        virtualNodeContext: VirtualNodeContext,
        createFunction: (VirtualNodeContext) -> CloseableSandboxGroupContext
    ): SandboxGroupContext {
        return contexts[virtualNodeContext] ?: run {
            val context = createFunction(virtualNodeContext)
            contexts.putIfAbsent(virtualNodeContext, context)?: context
        }
    }

    fun close() {
        contexts.forEach { (k, closeable) ->
            try {
                closeable.close()
            } catch(e: Exception) {
                logger.warn("Failed to close '$k' SandboxGroupContext", e)
            }
        }
    }
}

