package net.corda.interop.aliasinfo.cache.impl

import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.interop.InteropAliasIdentity
import net.corda.interop.aliasinfo.cache.AliasInfoCacheService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory


@Component(service = [AliasInfoCacheService::class])
class AliasInfoCacheServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
) : AliasInfoCacheService {
    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val lifecycleEventHandler = AliasInfoCacheServiceEventHandler(
        configurationReadService,
    )

    private val coordinatorName = LifecycleCoordinatorName.forComponent<AliasInfoCacheService>()
    private val coordinator = coordinatorFactory.createCoordinator(coordinatorName, lifecycleEventHandler)

    override fun getAliasIdentities(key: String): List<InteropAliasIdentity> {
        TODO("Not yet implemented")
    }

    override fun putAliasIdentity(key: String, value: InteropAliasIdentity) {
        TODO("Not yet implemented")
    }

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
