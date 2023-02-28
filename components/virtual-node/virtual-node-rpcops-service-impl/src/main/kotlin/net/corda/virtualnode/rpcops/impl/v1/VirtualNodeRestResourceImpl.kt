package net.corda.virtualnode.rpcops.impl.v1

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.crypto.core.ShortHash
import net.corda.data.ExceptionEnvelope
import net.corda.data.virtualnode.VirtualNodeAsynchronousRequest
import net.corda.data.virtualnode.VirtualNodeCreateRequest
import net.corda.data.virtualnode.VirtualNodeCreateResponse
import net.corda.data.virtualnode.VirtualNodeManagementRequest
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.data.virtualnode.VirtualNodeManagementResponseFailure
import net.corda.data.virtualnode.VirtualNodeStateChangeRequest
import net.corda.data.virtualnode.VirtualNodeStateChangeResponse
import net.corda.data.virtualnode.VirtualNodeUpgradeRequest
import net.corda.httprpc.PluggableRestResource
import net.corda.httprpc.security.CURRENT_REST_CONTEXT
import net.corda.httprpc.asynchronous.v1.AsyncResponse
import net.corda.httprpc.messagebus.MessageBusUtils.tryWithExceptionHandling
import net.corda.httprpc.response.ResponseEntity
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.cpiupload.endpoints.v1.CpiIdentifier
import net.corda.libs.virtualnode.endpoints.v1.VirtualNodeRestResource
import net.corda.libs.virtualnode.endpoints.v1.types.ChangeVirtualNodeStateResponse
import net.corda.libs.virtualnode.endpoints.v1.types.VirtualNodeInfo
import net.corda.libs.virtualnode.endpoints.v1.types.VirtualNodeRequest
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
import net.corda.schema.configuration.ConfigKeys
import net.corda.utilities.debug
import net.corda.utilities.time.ClockFactory
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.OperationalStatus
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.read.rpc.extensions.parseOrThrow
import net.corda.virtualnode.rpcops.common.VirtualNodeSender
import net.corda.virtualnode.rpcops.common.VirtualNodeSenderFactory
import net.corda.virtualnode.rpcops.impl.v1.ExceptionTranslator.Companion.translate
import net.corda.virtualnode.rpcops.impl.validation.impl.VirtualNodeValidationServiceImpl
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import java.time.Instant
import org.slf4j.LoggerFactory
import java.time.Duration
import net.corda.libs.virtualnode.endpoints.v1.types.HoldingIdentity as HoldingIdentityEndpointType
import java.lang.IllegalArgumentException
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.httprpc.exception.InternalServerException
import net.corda.httprpc.exception.InvalidInputDataException
import net.corda.httprpc.exception.InvalidStateChangeException
import net.corda.libs.virtualnode.common.constant.VirtualNodeStateTransitions
import net.corda.libs.virtualnode.common.exception.InvalidStateChangeRuntimeException

@Suppress("LongParameterList", "TooManyFunctions")
@Component(service = [PluggableRestResource::class])
internal class VirtualNodeRestResourceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = VirtualNodeSenderFactory::class)
    private val virtualNodeSenderFactory: VirtualNodeSenderFactory,
    @Reference(service = CpiInfoReadService::class)
    private val cpiInfoReadService: CpiInfoReadService,
    @Reference(service = ClockFactory::class)
    private var clockFactory: ClockFactory
) : VirtualNodeRestResource, PluggableRestResource<VirtualNodeRestResource>, Lifecycle {

    private companion object {
        private val requiredKeys = setOf(ConfigKeys.MESSAGING_CONFIG, ConfigKeys.REST_CONFIG)
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        private const val REGISTRATION = "REGISTRATION"
        private const val SENDER = "SENDER"
        private const val CONFIG_HANDLE = "CONFIG_HANDLE"
        private const val VIRTUAL_NODE_ASYNC_OPERATION_CLIENT_ID = "VIRTUAL_NODE_ASYNC_OPERATION_CLIENT"
    }

    private val clock = clockFactory.createUTCClock()
    private val virtualNodeValidationService = VirtualNodeValidationServiceImpl(
        virtualNodeInfoReadService,
        cpiInfoReadService
    )

    // Http RPC values
    override val targetInterface: Class<VirtualNodeRestResource> = VirtualNodeRestResource::class.java
    override val protocolVersion = 1

    // Lifecycle
    private val dependentComponents = DependentComponents.of(::virtualNodeInfoReadService)
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
        if (!isRunning) throw IllegalStateException(
            "${this.javaClass.simpleName} is not running! Its status is: ${lifecycleCoordinator.status}"
        )

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
        if (!isRunning) throw IllegalStateException(
            "${this.javaClass.simpleName} is not running! Its status is: ${lifecycleCoordinator.status}"
        )
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

    override fun upgradeVirtualNode(virtualNodeShortId: String, targetCpiFileChecksum: String): ResponseEntity<AsyncResponse> {
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
                CURRENT_REST_CONTEXT.get().principal
            )
        }

        return ResponseEntity.accepted(AsyncResponse(requestId))
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
        if (!isRunning) throw IllegalStateException(
            "${this.javaClass.simpleName} is not running! Its status is: ${lifecycleCoordinator.status}"
        )

        val sender = lifecycleCoordinator.getManagedResource<VirtualNodeSender>(SENDER)
            ?: throw IllegalStateException("Sender not initialized, check component status for ${this.javaClass.name}")

        return sender.sendAsync(key, request)
    }

    /**
     * Publishes a virtual node create request onto the message bus that results in persistence of a new virtual node
     *  in the database, as well as a copy of the persisted object being published back into the bus
     *
     * @property VirtualNodeRequest is contains the data we want to use to construct our virtual node
     * @throws IllegalStateException is thrown if the component isn't running and therefore able to service requests.
     * @return [VirtualNodeInfo] which is a data class containing information on the virtual node created
     *
     * @see VirtualNodeInfo
     */
    override fun createVirtualNode(request: VirtualNodeRequest): VirtualNodeInfo {
        val instant = clock.instant()
        if (!isRunning) throw IllegalStateException(
            "${this.javaClass.simpleName} is not running! Its status is: ${lifecycleCoordinator.status}"
        )
        validateX500Name(request.x500Name)

        val actor = CURRENT_REST_CONTEXT.get().principal
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
                    uniquenessDdlConnection,
                    uniquenessDmlConnection,
                    actor
                )
            )
        }
        val resp = tryWithExceptionHandling(logger, "Create vNode") {
            sendAndReceive(rpcRequest)
        }
        return when (val resolvedResponse = resp.responseType) {
            is VirtualNodeCreateResponse -> {
                // Convert response into expected type
                resolvedResponse.run {
                    VirtualNodeInfo(
                        HoldingIdentity(MemberX500Name.parse(x500Name), mgmGroupId).toEndpointType(),
                        CpiIdentifier.fromAvro(cpiIdentifier),
                        vaultDdlConnectionId,
                        vaultDmlConnectionId,
                        cryptoDdlConnectionId,
                        cryptoDmlConnectionId,
                        uniquenessDdlConnectionId,
                        uniquenessDmlConnectionId,
                        hsmConnectionId,
                        OperationalStatus.ACTIVE,
                        OperationalStatus.ACTIVE,
                        OperationalStatus.ACTIVE,
                        OperationalStatus.ACTIVE,
                    )
                }
            }
            is VirtualNodeManagementResponseFailure -> throw translate(resolvedResponse.exception)
            else -> throw UnknownResponseTypeException(resp.responseType::class.java.name)
        }
    }

    // Lookup and update the virtual node for the given virtual node short ID.
    //  This will update the last instance of said virtual node, sorted by CPI version
    @Suppress("ForbiddenComment")
    override fun updateVirtualNodeState(
        virtualNodeShortId: String,
        newState: String
    ): ChangeVirtualNodeStateResponse {
        val instant = clock.instant()
        // Lookup actor to keep track of which RPC user triggered an update
        val actor = CURRENT_REST_CONTEXT.get().principal
        logger.debug { "Received request to update state for $virtualNodeShortId to $newState by $actor at $instant" }
        if (!isRunning) throw IllegalStateException(
            "${this.javaClass.simpleName} is not running! Its status is: ${lifecycleCoordinator.status}"
        )
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

    /** Validates the [x500Name]. */
    private fun validateX500Name(x500Name: String) = try {
        MemberX500Name.parse(x500Name)
    } catch (e: Exception) {
        logger.warn("Configuration Management  X500 name \"$x500Name\" could not be parsed. Cause: ${e.message}")
        val message = "X500 name \"$x500Name\" could not be parsed. Cause: ${e.message}"
        throw InvalidInputDataException(message)
    }

    // Mandatory lifecycle methods - def to coordinator
    override val isRunning get() = lifecycleCoordinator.isRunning
    override fun start() = lifecycleCoordinator.start()
    override fun stop() = lifecycleCoordinator.stop()
}
