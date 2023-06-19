package net.corda.interop.aliasinfo.read.impl

import net.corda.configuration.read.ConfigurationReadService
import net.corda.interop.aliasinfo.read.InteropAliasInfoReadService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory


@Component(service = [InteropAliasInfoReadService::class])
class InteropAliasInfoReadServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
) : InteropAliasInfoReadService {
    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val lifecycleEventHandler = InteropAliasInfoReadServiceEventHandler(
        configurationReadService
    )

    private val coordinatorName = LifecycleCoordinatorName.forComponent<InteropAliasInfoReadService>()

    private val coordinator = coordinatorFactory.createCoordinator(coordinatorName, lifecycleEventHandler)

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        log.info("Component starting")
        coordinator.start()
    }

    override fun stop() {
        log.info("Component stopping")
        coordinator.stop()
    }
}
