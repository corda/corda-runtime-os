package net.corda.flow.rest.impl.v1

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.data.virtualnode.VirtualNodeInfo
import net.corda.flow.rest.v1.FlowClassRestResource
import net.corda.flow.rest.v1.types.response.StartableFlowsResponse
import net.corda.rest.PluggableRestResource
import net.corda.rest.exception.ResourceNotFoundException
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.read.rest.extensions.getByHoldingIdentityShortHashOrThrow
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Component(service = [FlowClassRestResource::class, PluggableRestResource::class])
class FlowClassRestResourceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = CpiInfoReadService::class)
    private val cpiInfoReadService: CpiInfoReadService,
    @Reference(service = PlatformInfoProvider::class)
    private val platformInfoProvider: PlatformInfoProvider,
) : FlowClassRestResource, PluggableRestResource<FlowClassRestResource>, Lifecycle {

    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun start() = coordinator.start()
    override fun stop() = coordinator.stop()
    override val isRunning: Boolean get() = coordinator.isRunning
    override val targetInterface: Class<FlowClassRestResource> = FlowClassRestResource::class.java
    override val protocolVersion get() = platformInfoProvider.localWorkerPlatformVersion

    private val coordinator = lifecycleCoordinatorFactory.createCoordinator<FlowClassRestResource>(::eventHandler)
    private val dependentComponents = DependentComponents.of(
        ::virtualNodeInfoReadService,
        ::cpiInfoReadService,
    )

    private fun eventHandler(event: LifecycleEvent, lifecycleCoordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                dependentComponents.registerAndStartAll(coordinator)
            }
            is RegistrationStatusChangeEvent -> {
                if (event.status == LifecycleStatus.UP) {
                    lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
                } else {
                    lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
                }
            }
            is StopEvent -> {
                dependentComponents.stopAll()
                coordinator.updateStatus(LifecycleStatus.DOWN)
            }
            else -> {
                log.error("Unexpected event $event!")
            }
        }
    }

    override fun getStartableFlows(holdingIdentityShortHash: String): StartableFlowsResponse {
        val vNode = getVirtualNode(holdingIdentityShortHash)
        val cpiMeta = getCPIMeta(vNode, holdingIdentityShortHash)
        return getFlowClassesFromCPI(cpiMeta)
    }

    private fun getCPIMeta(
        vNode: VirtualNodeInfo,
        holdingIdentityShortHash: String
    ): CpiMetadata {
        val vNodeCPIIdentifier = vNode.cpiIdentifier
        return cpiInfoReadService.get(CpiIdentifier.fromAvro(vNodeCPIIdentifier))
            ?: throw ResourceNotFoundException("CPI", holdingIdentityShortHash)
    }

    private fun getFlowClassesFromCPI(cpiMeta: CpiMetadata): StartableFlowsResponse {
        val flowClasses = cpiMeta.cpksMetadata.flatMap {
            it.cordappManifest.clientStartableFlows
        }
        return StartableFlowsResponse(flowClasses)
    }

    private fun getVirtualNode(holdingIdentityShortHash: String): VirtualNodeInfo {
        return virtualNodeInfoReadService.getByHoldingIdentityShortHashOrThrow(holdingIdentityShortHash).toAvro()
    }
}
