package net.corda.virtualnode.rpcops.impl.v1

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.crypto.core.ShortHash
import net.corda.data.ExceptionEnvelope
import net.corda.data.virtualnode.VirtualNodeAsynchronousRequest
import net.corda.data.virtualnode.VirtualNodeManagementRequest
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.data.virtualnode.VirtualNodeManagementResponseFailure
import net.corda.data.virtualnode.VirtualNodeOperationStatusRequest
import net.corda.data.virtualnode.VirtualNodeOperationStatusResponse
import net.corda.data.virtualnode.VirtualNodeStateChangeRequest
import net.corda.data.virtualnode.VirtualNodeStateChangeResponse
import net.corda.data.virtualnode.VirtualNodeUpgradeRequest
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.cpiupload.endpoints.v1.CpiIdentifier
import net.corda.libs.virtualnode.common.exception.VirtualNodeOperationNotFoundException
import net.corda.libs.virtualnode.common.constant.VirtualNodeStateTransitions
import net.corda.libs.virtualnode.common.exception.InvalidStateChangeRuntimeException
import net.corda.libs.virtualnode.endpoints.v1.VirtualNodeRestResource
import net.corda.libs.virtualnode.endpoints.v1.types.ChangeVirtualNodeStateResponse
import net.corda.libs.virtualnode.endpoints.v1.types.CreateVirtualNodeRequest
import net.corda.libs.virtualnode.endpoints.v1.types.VirtualNodeInfo
import net.corda.libs.virtualnode.endpoints.v1.types.VirtualNodeOperationStatus
import net.corda.libs.virtualnode.endpoints.v1.types.VirtualNodeOperationStatuses
import net.corda.libs.virtualnode.endpoints.v1.types.VirtualNodes
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.rest.PluggableRestResource
import net.corda.rest.asynchronous.v1.AsyncResponse
import net.corda.rest.exception.InternalServerException
import net.corda.rest.exception.InvalidInputDataException
import net.corda.rest.exception.InvalidStateChangeException
import net.corda.rest.exception.ResourceNotFoundException
import net.corda.rest.messagebus.MessageBusUtils.tryWithExceptionHandling
import net.corda.rest.response.ResponseEntity
import net.corda.rest.security.RestContextProvider
import net.corda.rest.security.RestContextProviderImpl
import net.corda.schema.configuration.ConfigKeys
import net.corda.utilities.debug
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.read.rest.extensions.parseOrThrow
import net.corda.virtualnode.rpcops.common.VirtualNodeSender
import net.corda.virtualnode.rpcops.common.VirtualNodeSenderFactory
import net.corda.virtualnode.rpcops.factories.RequestFactory
import net.corda.virtualnode.rpcops.factories.impl.RequestFactoryImpl
import net.corda.virtualnode.rpcops.impl.validation.VirtualNodeValidationService
import net.corda.virtualnode.rpcops.impl.validation.impl.VirtualNodeValidationServiceImpl
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import net.corda.libs.virtualnode.endpoints.v1.types.HoldingIdentity as HoldingIdentityEndpointType

@Suppress("LongParameterList", "TooManyFunctions")
@Component(service = [PluggableRestResource::class])
internal class VirtualNodeRestResourceImpl(
    coordinatorFactory: LifecycleCoordinatorFactory,
    configurationReadService: ConfigurationReadService,
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    private val virtualNodeSenderFactory: VirtualNodeSenderFactory,
    private val cpiInfoReadService: CpiInfoReadService,
    private val requestFactory: RequestFactory,
    private val clock: Clock,
    private var virtualNodeValidationService: VirtualNodeValidationService,
    private var restContextProvider: RestContextProvider
) : VirtualNodeRestResource, PluggableRestResource<VirtualNodeRestResource>, Lifecycle {

    @Activate
    constructor(
        @Reference(service = LifecycleCoordinatorFactory::class)
        coordinatorFactory: LifecycleCoordinatorFactory,
        @Reference(service = ConfigurationReadService::class)
        configurationReadService: ConfigurationReadService,
        @Reference(service = VirtualNodeInfoReadService::class)
        virtualNodeInfoReadService: VirtualNodeInfoReadService,
        @Reference(service = VirtualNodeSenderFactory::class)
        virtualNodeSenderFactory: VirtualNodeSenderFactory,
        @Reference(service = CpiInfoReadService::class)
        cpiInfoReadService: CpiInfoReadService
    ) : this(
        coordinatorFactory,
        configurationReadService,
        virtualNodeInfoReadService,
        virtualNodeSenderFactory,
        cpiInfoReadService,
        RequestFactoryImpl(
            RestContextProviderImpl(),
            UTCClock()
        ),
        UTCClock(),
        VirtualNodeValidationServiceImpl(virtualNodeInfoReadService, cpiInfoReadService),
        RestContextProviderImpl()
    )

    private companion object {
        private val requiredKeys = setOf(ConfigKeys.MESSAGING_CONFIG, ConfigKeys.REST_CONFIG)
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        private const val REGISTRATION = "REGISTRATION"
        private const val SENDER = "SENDER"
        private const val CONFIG_HANDLE = "CONFIG_HANDLE"
        private const val VIRTUAL_NODE_ASYNC_OPERATION_CLIENT_ID = "VIRTUAL_NODE_ASYNC_OPERATION_CLIENT"
    }

    // Http RPC values
    override val targetInterface: Class<VirtualNodeRestResource> = VirtualNodeRestResource::class.java
    override val protocolVersion = 1

    // Lifecycle
    private val dependentComponents = DependentComponents.of(
        ::virtualNodeInfoReadService,
        ::cpiInfoReadService
    )

    private val lifecycleCoordinator = coordinatorFactory.createCoordinator(
        LifecycleCoordinatorName.forComponent<VirtualNodeRestResource>()
    ) { event: LifecycleEvent, coordinator: LifecycleCoordinator ->
        when (event) {
            is StartEvent -> {
                configurationReadService.start()
                coordinator.createManagedResource(REGISTRATION) {
                    coordinator.followStatusChangesByName(
                        setOf(
                            LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                        )
                    )
                }
                dependentComponents.registerAndStartAll(coordinator)
                coordinator.updateStatus(LifecycleStatus.UP)
            }
            is StopEvent -> coordinator.updateStatus(LifecycleStatus.DOWN)
            is RegistrationStatusChangeEvent -> {
                when (event.status) {
                    LifecycleStatus.ERROR -> {
                        coordinator.closeManagedResources(setOf(CONFIG_HANDLE))
                        coordinator.postEvent(StopEvent(errored = true))
                    }
                    LifecycleStatus.UP -> {
                        // Receive updates to the RPC and Messaging config
                        coordinator.createManagedResource(CONFIG_HANDLE) {
                            configurationReadService.registerComponentForUpdates(
                                coordinator,
                                requiredKeys
                            )
                        }
                    }
                    else -> logger.debug { "Unexpected status: ${event.status}" }
                }
                coordinator.updateStatus(event.status)
            }
            is ConfigChangedEvent -> {
                if (requiredKeys.all { it in event.config.keys } and event.keys.any { it in requiredKeys }) {
                    //val rpcConfig = event.config.getConfig(ConfigKeys.REST_CONFIG)
                    val messagingConfig = event.config.getConfig(ConfigKeys.MESSAGING_CONFIG)
                    val duration = Duration.ofSeconds(60) // Temporary change till CORE-7646 properly resolved
                    // Duration.ofMillis(rpcConfig.getInt(ConfigKeys.REST_ENDPOINT_TIMEOUT_MILLIS).toLong())
                    // Make sender unavailable while we're updating
                    coordinator.updateStatus(LifecycleStatus.DOWN)
                    coordinator.createManagedResource(SENDER) {
                        virtualNodeSenderFactory.createSender(
                            duration, messagingConfig, PublisherConfig(VIRTUAL_NODE_ASYNC_OPERATION_CLIENT_ID)
                        )
                    }
                    coordinator.updateStatus(LifecycleStatus.UP)
                }
            }
        }
    }

    /**
     * Sends the [request] to the configuration management topic on bus.
     *
     * @property request is a [VirtualNodeManagementRequest]. This an enveloper around the intended request
     * @throws CordaRuntimeException If the message could not be published.
     * @return [VirtualNodeManagementResponse] which is an envelope around the actual response.
     *  This response corresponds to the [VirtualNodeManagementRequest] received by the function
     * @see VirtualNodeManagementRequest
     * @see VirtualNodeManagementResponse
     */
    private fun sendAndReceive(request: VirtualNodeManagementRequest): VirtualNodeManagementResponse {
        val sender = lifecycleCoordinator.getManagedResource<VirtualNodeSender>(SENDER)
            ?: throw IllegalStateException("Sender not initialized, check component status for ${this.javaClass.name}")

        return sender.sendAndReceive(request)
    }

    /**
     * Retrieves the list of virtual nodes stored on the message bus
     *
     * @throws IllegalStateException is thrown if the component isn't running and therefore able to service requests.
     * @return [VirtualNodes] which is a list of [VirtualNodeInfo]
     *
     * @see VirtualNodes
     * @see VirtualNodeInfo
     */
    override fun getAllVirtualNodes(): VirtualNodes {
        return VirtualNodes(virtualNodeInfoReadService.getAll().map { it.toEndpointType() })
    }

    /**
     * Retrieves the VirtualNodeInfo for a virtual node with the given ShortHash from the message bus
     *
     * @throws ResourceNotFoundException is thrown if no Virtual node with the given ShortHash was found.
     * @return [VirtualNodeInfo] for the corresponding ShortHash
     *
     * @see VirtualNodes
     * @see VirtualNodeInfo
     */
    override fun getVirtualNode(holdingIdentityShortHash: String): VirtualNodeInfo {
        val shortHash = ShortHash.parseOrThrow(holdingIdentityShortHash)
        val virtualNode = virtualNodeInfoReadService.getByHoldingIdentityShortHash(shortHash)
            ?: throw ResourceNotFoundException("VirtualNode with shortHash $holdingIdentityShortHash could not be found.")

        return virtualNode.toEndpointType()
    }

    override fun upgradeVirtualNode(
        virtualNodeShortId: String,
        targetCpiFileChecksum: String
    ): ResponseEntity<AsyncResponse> {
        val currentVirtualNode = virtualNodeValidationService.validateAndGetVirtualNode(virtualNodeShortId)
        val currentCpi = requireNotNull(cpiInfoReadService.get(currentVirtualNode.cpiIdentifier)) {
            "Current CPI ${currentVirtualNode.cpiIdentifier} associated with virtual node $virtualNodeShortId was not found."
        }
        val targetCpi = virtualNodeValidationService.validateAndGetCpiByChecksum(targetCpiFileChecksum)
        virtualNodeValidationService.validateCpiUpgradePrerequisites(currentCpi, targetCpi)

        val requestId = tryWithExceptionHandling(logger, "Upgrade vNode") {
            sendAsynchronousRequest(
                Instant.now(),
                virtualNodeShortId,
                currentCpi.fileChecksum.toHexString(),
                targetCpi.fileChecksum.toHexString(),
                restContextProvider.principal
            )
        }

        return ResponseEntity.accepted(AsyncResponse(requestId))
    }

    override fun getVirtualNodeOperationStatus(requestId: String): VirtualNodeOperationStatuses {
        val instant = clock.instant()

        // Send request for update to kafka, processed by the db worker in VirtualNodeWriterProcessor
        val rpcRequest = VirtualNodeManagementRequest(
            instant,
            VirtualNodeOperationStatusRequest(requestId)
        )

        // Actually send request and await response message on bus
        val resp: VirtualNodeManagementResponse = sendAndReceive(rpcRequest)

        return when (val resolvedResponse = resp.responseType) {
            is VirtualNodeOperationStatusResponse -> {
                resolvedResponse.run {
                    val statuses = this.operationHistory.map{
                        VirtualNodeOperationStatus(
                            it.requestId,
                            it.requestData,
                            it.requestTimestamp,
                            it.latestUpdateTimestamp,
                            it.heartbeatTimestamp,
                            it.state,
                            it.errors
                        )
                    }

                    VirtualNodeOperationStatuses(this.requestId, statuses)
                }
            }
            is VirtualNodeManagementResponseFailure -> throw handleFailure(resolvedResponse.exception)
            else -> throw UnknownResponseTypeException(resp.responseType::class.java.name)
        }
    }

    private fun sendAsynchronousRequest(
        requestTime: Instant,
        virtualNodeShortId: String,
        currentCpiFileChecksum: String,
        targetCpiFileChecksum: String,
        actor: String
    ): String {
        val requestId = generateUpgradeRequestId(virtualNodeShortId, currentCpiFileChecksum, targetCpiFileChecksum)

        sendAsync(
            virtualNodeShortId,
            VirtualNodeAsynchronousRequest(
                requestTime, requestId, VirtualNodeUpgradeRequest(virtualNodeShortId, targetCpiFileChecksum, actor)
            )
        )

        return requestId
    }

    /**
     * Virtual node upgrade request ID deterministically generated using the virtual node identifier, current CPI file checksum
     * and target CPI file checksum. This provides a level of idempotency preventing the same upgrade from triggering more than once.
     */
    private fun generateUpgradeRequestId(
        virtualNodeShortId: String, currentCpiFileChecksum: String, targetCpiFileChecksum: String
    ): String {
        return virtualNodeShortId.take(12) + currentCpiFileChecksum.take(12) + targetCpiFileChecksum.take(12)
    }

    private fun sendAsync(key: String, request: VirtualNodeAsynchronousRequest) {
        val sender = lifecycleCoordinator.getManagedResource<VirtualNodeSender>(SENDER)
            ?: throw IllegalStateException("Sender not initialized, check component status for ${this.javaClass.name}")

        return sender.sendAsync(key, request)
    }

    /**
     * Publishes a virtual node create request onto the message bus.
     *
     * @property CreateVirtualNodeRequest is contains the data we want to use to construct our virtual node
     * @throws InvalidInputDataException if the request in invalid.
     * @throws InternalServerException if the requested CPI has invalid metadata.
     * @throws ServiceUnavailableException is thrown if the component isn't running.
     * @return [ResponseEntity] containing the request ID for the create virtual node request.
     */
    override fun createVirtualNode(request: CreateVirtualNodeRequest): ResponseEntity<AsyncResponse> {
        val groupId = virtualNodeValidationService.validateAndGetGroupId(request)

        val holdingIdentity = requestFactory.createHoldingIdentity(groupId, request)

        virtualNodeValidationService.validateVirtualNodeDoesNotExist(holdingIdentity)

        val asyncRequest = requestFactory.createVirtualNodeRequest(holdingIdentity, request)

        sendAsync(asyncRequest.requestId, asyncRequest)

        return ResponseEntity.accepted(AsyncResponse(asyncRequest.requestId))
    }

    // Lookup and update the virtual node for the given virtual node short ID.
    // This will update the last instance of said virtual node, sorted by CPI version
    @Suppress("ForbiddenComment")
    override fun updateVirtualNodeState(
        virtualNodeShortId: String,
        newState: String
    ): ChangeVirtualNodeStateResponse {
        val instant = clock.instant()
        // Lookup actor to keep track of which RPC user triggered an update
        val actor = restContextProvider.principal
        logger.debug { "Received request to update state for $virtualNodeShortId to $newState by $actor at $instant" }

        validateStateChange(virtualNodeShortId, newState)

        // Send request for update to kafka, precessed by the db worker in VirtualNodeWriterProcessor
        val rpcRequest = VirtualNodeManagementRequest(
            instant,
            VirtualNodeStateChangeRequest(
                virtualNodeShortId,
                newState,
                actor
            )
        )
        // Actually send request and await response message on bus
        val resp = tryWithExceptionHandling(logger, "Update vNode state") {
            sendAndReceive(rpcRequest)
        }
        logger.debug { "Received response to update for $virtualNodeShortId to $newState by $actor" }

        return when (val resolvedResponse = resp.responseType) {
            is VirtualNodeStateChangeResponse -> {
                resolvedResponse.run {
                    ChangeVirtualNodeStateResponse(holdingIdentityShortHash, newState)
                }
            }
            is VirtualNodeManagementResponseFailure -> throw handleFailure(resolvedResponse.exception)
            else -> throw UnknownResponseTypeException(resp.responseType::class.java.name)
        }
    }

    private fun validateStateChange(virtualNodeShortId: String, newState: String) {
        try {
            VirtualNodeStateTransitions.valueOf(newState.uppercase())
        } catch (e: IllegalArgumentException) {
            throw InvalidInputDataException(details = mapOf("newState" to "must be one of ACTIVE, MAINTENANCE"))
        }
        getVirtualNode(virtualNodeShortId)
    }

    private fun handleFailure(exception: ExceptionEnvelope?): java.lang.Exception {
        if (exception == null) {
            logger.warn("Configuration Management request was unsuccessful but no exception was provided.")
            return InternalServerException("Request was unsuccessful but no exception was provided.")
        }
        logger.warn(
            "Remote request failed with exception of type ${exception.errorType}: ${exception.errorMessage}"
        )
        return when(exception.errorType) {
            InvalidStateChangeRuntimeException::class.java.name ->  InvalidStateChangeException(exception.errorMessage)
            VirtualNodeOperationNotFoundException::class.java.name -> ResourceNotFoundException(exception.errorMessage)
            else -> InternalServerException(exception.errorMessage)
        }
    }

    private fun HoldingIdentity.toEndpointType(): HoldingIdentityEndpointType =
        HoldingIdentityEndpointType(x500Name.toString(), groupId, shortHash.value, fullHash)

    private fun net.corda.virtualnode.VirtualNodeInfo.toEndpointType(): VirtualNodeInfo =
        VirtualNodeInfo(
            holdingIdentity.toEndpointType(),
            cpiIdentifier.toEndpointType(),
            vaultDdlConnectionId?.toString(),
            vaultDmlConnectionId.toString(),
            cryptoDdlConnectionId?.toString(),
            cryptoDmlConnectionId.toString(),
            uniquenessDdlConnectionId?.toString(),
            uniquenessDmlConnectionId.toString(),
            hsmConnectionId.toString(),
            flowP2pOperationalStatus,
            flowStartOperationalStatus,
            flowOperationalStatus,
            vaultDbOperationalStatus,
            operationInProgress
        )

    private fun net.corda.libs.packaging.core.CpiIdentifier.toEndpointType(): CpiIdentifier =
        CpiIdentifier(name, version, signerSummaryHash.toString())

    // Mandatory lifecycle methods - def to coordinator
    override val isRunning get() = lifecycleCoordinator.isRunning
    override fun start() = lifecycleCoordinator.start()
    override fun stop() = lifecycleCoordinator.stop()
}
