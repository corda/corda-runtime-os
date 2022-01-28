package net.corda.virtualnode.rpcops.impl

import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.virtualnode.common.endpoints.LateInitRPCOpsEventHandler
import net.corda.virtualnode.rpcops.VirtualNodeRPCOpsService
import net.corda.virtualnode.rpcops.impl.internal.VirtualNodeRPCOpsInternal
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/** An implementation of [VirtualNodeRPCOpsService]. */
@Component(immediate = true, service = [VirtualNodeRPCOpsService::class])
internal class VirtualNodeRPCOpsServiceImpl @Activate constructor(
    @Reference(service = ConfigurationReadService::class)
    configReadService: ConfigurationReadService,
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = VirtualNodeRPCOpsInternal::class)
    virtualNodeRPCOps: VirtualNodeRPCOpsInternal
) : VirtualNodeRPCOpsService {

    private val coordinator = let {
        val eventHandler = LateInitRPCOpsEventHandler(configReadService, virtualNodeRPCOps)
        coordinatorFactory.createCoordinator<VirtualNodeRPCOpsService>(eventHandler)
    }

    override val isRunning get() = coordinator.isRunning

    override fun start() = coordinator.start()

    override fun stop() = coordinator.stop()
}