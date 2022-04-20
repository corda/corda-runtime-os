package net.corda.sandboxgroupcontext.service.impl

import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.v5.base.util.loggerFor

class SandboxGroupContextCache(private val cacheSize: Long = 25) {
    private companion object {
        private val logger = loggerFor<SandboxGroupContextCache>()
    }
    private val contexts = Caffeine.newBuilder()
        .maximumSize(cacheSize)
        .build<VirtualNodeContext, CloseableSandboxGroupContext>()

    fun remove(virtualNodeContext: VirtualNodeContext) {
        contexts.asMap().remove(virtualNodeContext)?.close()
    }

    fun get(
        virtualNodeContext: VirtualNodeContext,
        createFunction: (VirtualNodeContext) -> CloseableSandboxGroupContext
    ): SandboxGroupContext {
        return contexts.get(virtualNodeContext) {
            createFunction(virtualNodeContext)
        }
    }

    fun close() {
        contexts.asMap().forEach { (k, closeable) ->
            try {
                closeable.close()
            } catch(e: Exception) {
                logger.warn("Failed to close '$k' SandboxGroupContext", e)
            }
        }
    }
}

