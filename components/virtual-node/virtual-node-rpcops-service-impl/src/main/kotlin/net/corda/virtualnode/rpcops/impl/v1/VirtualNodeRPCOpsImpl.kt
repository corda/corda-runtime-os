package net.corda.virtualnode.rpcops.impl.v1

import net.corda.data.virtualnode.VirtualNodeCreateRequest
import net.corda.data.virtualnode.VirtualNodeCreateResponse
import net.corda.data.virtualnode.VirtualNodeManagementRequest
import net.corda.data.virtualnode.VirtualNodeManagementResponseFailure
import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.exception.InternalServerException
import net.corda.httprpc.exception.InvalidInputDataException
import net.corda.httprpc.security.CURRENT_RPC_CONTEXT
import net.corda.libs.cpiupload.endpoints.v1.CpiIdentifier
import net.corda.libs.virtualnode.endpoints.v1.VirtualNodeRPCOps
import net.corda.libs.virtualnode.endpoints.v1.types.VirtualNodeInfo
import net.corda.libs.virtualnode.endpoints.v1.types.VirtualNodeRequest
import net.corda.libs.virtualnode.endpoints.v1.types.VirtualNodes
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.StartEvent
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.rpcops.common.VirtualNodeSenderService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import net.corda.libs.virtualnode.endpoints.v1.types.HoldingIdentity as HoldingIdentityEndpointType

/** An implementation of [VirtualNodeRPCOpsInternal]. */
@Suppress("Unused")
@Component(service = [PluggableRPCOps::class])
// Primary constructor is for test. This is until a clock service is available
internal class VirtualNodeRPCOpsImpl @VisibleForTesting constructor(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    private val virtualNodeSenderService: VirtualNodeSenderService,
    private var clock: Clock
) : VirtualNodeRPCOps, PluggableRPCOps<VirtualNodeRPCOps>, Lifecycle {

    @Activate constructor(
        @Reference(service = LifecycleCoordinatorFactory::class)
        coordinatorFactory: LifecycleCoordinatorFactory,
        @Reference(service = VirtualNodeInfoReadService::class)
        virtualNodeInfoReadService: VirtualNodeInfoReadService,
        @Reference(service = VirtualNodeSenderService::class)
        virtualNodeSenderService: VirtualNodeSenderService
    ) : this(coordinatorFactory, virtualNodeInfoReadService, virtualNodeSenderService, UTCClock())

    private companion object {
        val logger = contextLogger()
    }

    private val dependentComponents = DependentComponents.of(
        ::virtualNodeInfoReadService,
        ::virtualNodeSenderService
    )

    private val coordinator = coordinatorFactory.createCoordinator(
        LifecycleCoordinatorName.forComponent<VirtualNodeRPCOps>()
    ) { event: LifecycleEvent, coordinator: LifecycleCoordinator ->
        logger.info(event.toString())
        logger.info(coordinator.toString())
        when (event) {
            is StartEvent -> dependentComponents.registerAndStartAll(coordinator)
        }
    }

    override val targetInterface: Class<VirtualNodeRPCOps> = VirtualNodeRPCOps::class.java
    override val protocolVersion = 1

    override fun createVirtualNode(request: VirtualNodeRequest): VirtualNodeInfo {
        val instant = clock.instant()
        validateX500Name(request.x500Name)

        val actor = CURRENT_RPC_CONTEXT.get().principal
        val rpcRequest = with(request) {
            VirtualNodeManagementRequest(
                instant,
                VirtualNodeCreateRequest(
                    x500Name,
                    cpiFileChecksum,
                    vaultDdlConnection,
                    vaultDmlConnection,
                    cryptoDdlConnection,
                    cryptoDmlConnection,
                    actor
                )
            )
        }
        val resp = virtualNodeSenderService.sendAndReceive(rpcRequest)
        logger.info(resp.responseType.toString())

        return when (val resolvedResponse = resp.responseType) {
            is VirtualNodeCreateResponse -> {
                VirtualNodeInfo(
                    HoldingIdentity(resolvedResponse.x500Name, resolvedResponse.mgmGroupId).toEndpointType(),
                    CpiIdentifier.fromAvro(resolvedResponse.cpiIdentifier),
                    resolvedResponse.vaultDdlConnectionId,
                    resolvedResponse.vaultDmlConnectionId,
                    resolvedResponse.cryptoDdlConnectionId,
                    resolvedResponse.cryptoDmlConnectionId,
                    resolvedResponse.hsmConnectionId,
                    resolvedResponse.virtualNodeState
                )
            }
            is VirtualNodeManagementResponseFailure -> {
                val exception = resolvedResponse.exception
                if (exception == null) {
                    logger.warn("Configuration Management request was unsuccessful but no exception was provided.")
                    throw InternalServerException("Request was unsuccessful but no exception was provided.")
                }
                logger.warn("Remote request to create virtual node responded with exception: ${exception.errorMessage}")
                throw InternalServerException(exception.errorMessage)
            }
            else -> throw UnknownResponseTypeException(resp.responseType::class.java.name)
        }
    }

    override fun getAllVirtualNodes(): VirtualNodes {
        return VirtualNodes(virtualNodeInfoReadService.getAll().map { it.toEndpointType() })
    }

    private fun HoldingIdentity.toEndpointType(): HoldingIdentityEndpointType =
        HoldingIdentityEndpointType(x500Name, groupId, shortHash, fullHash)

    private fun net.corda.virtualnode.VirtualNodeInfo.toEndpointType(): VirtualNodeInfo =
        VirtualNodeInfo(
            holdingIdentity.toEndpointType(),
            cpiIdentifier.toEndpointType(),
            vaultDdlConnectionId?.toString(),
            vaultDmlConnectionId.toString(),
            cryptoDdlConnectionId?.toString(),
            cryptoDmlConnectionId.toString(),
            hsmConnectionId.toString(),
            state.name
        )

    private fun net.corda.libs.packaging.core.CpiIdentifier.toEndpointType(): CpiIdentifier =
        CpiIdentifier(name, version, signerSummaryHash?.toString())

    /** Validates the [x500Name]. */
    private fun validateX500Name(x500Name: String) = try {
        MemberX500Name.parse(x500Name)
    } catch (e: Exception) {
        logger.warn("Configuration Management  X500 name \"$x500Name\" could not be parsed. Cause: ${e.message}")
        val message = "X500 name \"$x500Name\" could not be parsed. Cause: ${e.message}"
        throw InvalidInputDataException(message)
    }

    // Mandatory lifecycle methods - def to coordinator
    override val isRunning get() = coordinator.isRunning
    override fun start() = coordinator.start()
    override fun stop() = coordinator.close()
}
