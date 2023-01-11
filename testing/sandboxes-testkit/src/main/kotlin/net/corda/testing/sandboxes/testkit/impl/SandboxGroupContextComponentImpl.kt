package net.corda.testing.sandboxes.testkit.impl

import java.time.Duration
import java.util.concurrent.CompletableFuture
import net.corda.sandboxgroupcontext.SandboxGroupContextService
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.service.CacheControl
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.v5.base.util.loggerFor
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking

@Suppress("unused")
@Component(service = [ SandboxGroupContextComponent::class ])
@ServiceRanking(SandboxSetup.SANDBOX_SERVICE_RANKING)
class SandboxGroupContextComponentImpl @Activate constructor(
    @Reference
    private val sandboxGroupContextService: SandboxGroupContextService
) : SandboxGroupContextComponent, SandboxGroupContextService by sandboxGroupContextService {
    private val logger = loggerFor<SandboxGroupContextComponentImpl>()

    override val isRunning: Boolean = true

    override fun initCache(capacity: Long) {
        (sandboxGroupContextService as? CacheControl
            ?: throw IllegalStateException("Cannot initialize sandbox cache")).initCache(capacity)
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

    override fun start() {
        logger.info("Started")
    }

    override fun stop() {
        logger.info("Stopped")
    }
}
