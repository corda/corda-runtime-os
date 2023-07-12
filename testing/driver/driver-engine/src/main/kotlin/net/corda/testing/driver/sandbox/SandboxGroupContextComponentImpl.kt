package net.corda.testing.driver.sandbox

import java.time.Duration
import java.util.concurrent.CompletableFuture
import net.corda.sandboxgroupcontext.SandboxGroupContextService
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.service.CacheControl
import net.corda.sandboxgroupcontext.service.EvictionListener
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking
import org.slf4j.LoggerFactory

@Suppress("unused")
@Component(service = [ SandboxGroupContextComponent::class ], property = [ DRIVER_SERVICE ])
@ServiceRanking(DRIVER_SERVICE_RANKING)
class SandboxGroupContextComponentImpl @Activate constructor(
    @Reference
    private val sandboxGroupContextService: SandboxGroupContextService
) : SandboxGroupContextComponent, SandboxGroupContextService by sandboxGroupContextService {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override val isRunning: Boolean
        get() = true

    override fun resizeCache(type: SandboxGroupType, capacity: Long) {
        (sandboxGroupContextService as? CacheControl
            ?: throw IllegalStateException("Cannot initialize sandbox cache")).resizeCache(type, capacity)
    }

    override fun remove(virtualNodeContext: VirtualNodeContext): CompletableFuture<*>? {
        return (sandboxGroupContextService as? CacheControl
            ?: throw IllegalStateException("Cannot remove sandbox from cache")).remove(virtualNodeContext)
    }

    override fun flushCache(): CompletableFuture<*> {
        return (sandboxGroupContextService as? CacheControl
            ?: throw IllegalStateException("Cannot flush sandbox cache")).flushCache()
    }

    @Throws(InterruptedException::class)
    override fun waitFor(completion: CompletableFuture<*>, duration: Duration): Boolean {
        return (sandboxGroupContextService as? CacheControl
            ?: throw IllegalStateException("Cannot wait for sandbox cache to flush")).waitFor(completion, duration)
    }

    override fun addEvictionListener(type: SandboxGroupType, listener: EvictionListener): Boolean {
        return (sandboxGroupContextService as? CacheControl
            ?: throw IllegalStateException("Failed to add eviction listener")).addEvictionListener(type, listener)
    }

    override fun removeEvictionListener(type: SandboxGroupType, listener: EvictionListener): Boolean {
        return (sandboxGroupContextService as? CacheControl
            ?: throw IllegalStateException("Failed to remove eviction listener")).removeEvictionListener(type, listener)
    }

    override fun start() {
        logger.info("Started")
    }

    override fun stop() {
        logger.info("Stopped")
    }
}
