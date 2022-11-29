package net.corda.testing.sandboxes.testkit.impl

import net.corda.sandboxgroupcontext.SandboxGroupContextService
import net.corda.sandboxgroupcontext.service.CacheConfiguration
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
        (sandboxGroupContextService as? CacheConfiguration)?.initCache(capacity)
            ?: throw IllegalStateException("Cannot initialize sandbox cache")
    }

    override fun start() {
        logger.info("Started")
    }

    override fun stop() {
        logger.info("Stopped")
    }
}
