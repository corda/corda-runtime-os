package net.corda.configuration.rpcops.impl

import net.corda.configuration.read.ConfigurationReadService
import net.corda.configuration.rpcops.ConfigRPCOpsService
import net.corda.configuration.rpcops.impl.v1.ConfigRPCOpsInternal
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/** An implementation of [ConfigRPCOpsService]. */
@Component(immediate = true, service = [ConfigRPCOpsService::class])
internal class ConfigRPCOpsServiceImpl @Activate constructor(
    @Reference(service = ConfigurationReadService::class)
    private val configReadService: ConfigurationReadService,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigRPCOpsInternal::class)
    private val configRPCOps: ConfigRPCOpsInternal
) : ConfigRPCOpsService {

    private val coordinator = let {
        val eventHandler = ConfigRPCOpsEventHandler(configReadService, configRPCOps)
        coordinatorFactory.createCoordinator<ConfigRPCOpsServiceImpl>(eventHandler)
    }

    override val isRunning get() = coordinator.isRunning

    override fun start() = coordinator.start()

    override fun stop() = coordinator.stop()
}