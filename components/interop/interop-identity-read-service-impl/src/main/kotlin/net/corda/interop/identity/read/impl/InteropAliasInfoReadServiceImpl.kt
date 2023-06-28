package net.corda.interop.identity.read.impl

import net.corda.configuration.read.ConfigurationReadService
import net.corda.interop.identity.cache.InteropIdentityCacheService
import net.corda.interop.identity.read.InteropAliasInfoReadService
import net.corda.lifecycle.DependentComponents
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
    @Reference(service = InteropIdentityCacheService::class)
    private val interopIdentityCacheService: InteropIdentityCacheService
) : InteropAliasInfoReadService {
    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val lifecycleEventHandler = InteropAliasInfoReadServiceEventHandler(
        configurationReadService
    )

    private val dependentComponents = DependentComponents.of(
        ::configurationReadService,
        ::interopIdentityCacheService
    )

    private val coordinatorName = LifecycleCoordinatorName.forComponent<InteropAliasInfoReadService>()
    private val coordinator = coordinatorFactory.createCoordinator(coordinatorName, dependentComponents, lifecycleEventHandler)

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        // Use debug rather than info
        log.info("Component starting")
        coordinator.start()
    }

    override fun stop() {
        //  Use debug rather than info
        log.info("Component stopping")
        coordinator.stop()
    }
}
