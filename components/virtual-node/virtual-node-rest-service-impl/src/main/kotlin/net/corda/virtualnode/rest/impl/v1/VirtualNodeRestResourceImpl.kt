package net.corda.virtualnode.rest.impl.v1

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.crypto.core.ShortHash
import net.corda.data.ExceptionEnvelope
import net.corda.data.virtualnode.DbTypes
import net.corda.data.virtualnode.VirtualNodeAsynchronousRequest
import net.corda.data.virtualnode.VirtualNodeCreateStatusResponse
import net.corda.data.virtualnode.VirtualNodeManagementRequest
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.data.virtualnode.VirtualNodeManagementResponseFailure
import net.corda.data.virtualnode.VirtualNodeOperationStatus
import net.corda.data.virtualnode.VirtualNodeOperationStatusRequest
import net.corda.data.virtualnode.VirtualNodeOperationStatusResponse
import net.corda.data.virtualnode.VirtualNodeOperationalState
import net.corda.data.virtualnode.VirtualNodeSchemaRequest
import net.corda.data.virtualnode.VirtualNodeSchemaResponse
import net.corda.data.virtualnode.VirtualNodeStateChangeRequest
import net.corda.data.virtualnode.VirtualNodeStateChangeResponse
import net.corda.data.virtualnode.VirtualNodeUpdateDbStatusResponse
import net.corda.data.virtualnode.VirtualNodeUpgradeRequest
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.external.messaging.serialization.ExternalMessagingRouteConfigSerializerImpl
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.libs.virtualnode.common.constant.VirtualNodeStateTransitions
import net.corda.libs.virtualnode.common.exception.LiquibaseDiffCheckFailedException
import net.corda.libs.virtualnode.common.exception.VirtualNodeOperationBadRequestException
import net.corda.libs.virtualnode.common.exception.VirtualNodeOperationNotFoundException
import net.corda.libs.virtualnode.endpoints.v1.VirtualNodeRestResource
import net.corda.libs.virtualnode.endpoints.v1.types.ChangeVirtualNodeStateResponse
import net.corda.libs.virtualnode.endpoints.v1.types.CreateVirtualNodeRequestType.CreateVirtualNodeRequest
import net.corda.libs.virtualnode.endpoints.v1.types.CreateVirtualNodeRequestType.JsonCreateVirtualNodeRequest
import net.corda.libs.virtualnode.endpoints.v1.types.UpdateVirtualNodeDbRequest
import net.corda.libs.virtualnode.endpoints.v1.types.VirtualNodeInfo
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
import net.corda.rest.asynchronous.v1.AsyncOperationState
import net.corda.rest.asynchronous.v1.AsyncOperationStatus
import net.corda.rest.asynchronous.v1.AsyncResponse
import net.corda.rest.exception.BadRequestException
import net.corda.rest.exception.InternalServerException
import net.corda.rest.exception.InvalidInputDataException
import net.corda.rest.exception.InvalidStateChangeException
import net.corda.rest.exception.ResourceNotFoundException
import net.corda.rest.exception.ServiceUnavailableException
import net.corda.rest.messagebus.MessageBusUtils.tryWithExceptionHandling
import net.corda.rest.response.ResponseEntity
import net.corda.rest.security.RestContextProvider
import net.corda.rest.security.RestContextProviderImpl
import net.corda.schema.configuration.ConfigKeys
import net.corda.utilities.debug
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.read.rest.extensions.parseOrThrow
import net.corda.virtualnode.rest.common.VirtualNodeSender
import net.corda.virtualnode.rest.common.VirtualNodeSenderFactory
import net.corda.virtualnode.rest.converters.MessageConverter
import net.corda.virtualnode.rest.converters.impl.MessageConverterImpl
import net.corda.virtualnode.rest.factories.RequestFactory
import net.corda.virtualnode.rest.factories.impl.RequestFactoryImpl
import net.corda.virtualnode.rest.impl.validation.VirtualNodeValidationService
import net.corda.virtualnode.rest.impl.validation.impl.VirtualNodeValidationServiceImpl
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

@Suppress("LongParameterList", "TooManyFunctions")
@Component(service = [PluggableRestResource::class])
internal class VirtualNodeRestResourceImpl(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val configurationReadService: ConfigurationReadService,
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    private val virtualNodeSenderFactory: VirtualNodeSenderFactory,
    private val cpiInfoReadService: CpiInfoReadService,
    private val requestFactory: RequestFactory,
    private val clock: Clock,
    private val virtualNodeValidationService: VirtualNodeValidationService,
    private val restContextProvider: RestContextProvider,
    private val messageConverter: MessageConverter,
    private val platformInfoProvider: PlatformInfoProvider
) : VirtualNodeRestResource, PluggableRestResource<VirtualNodeRestResource>, Lifecycle {

    @Suppress("Unused")
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
        cpiInfoReadService: CpiInfoReadService,
        @Reference(service = PlatformInfoProvider::class)
        platformInfoProvider: PlatformInfoProvider
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
        RestContextProviderImpl(),
        MessageConverterImpl(ExternalMessagingRouteConfigSerializerImpl()),
        platformInfoProvider
    )

    private companion object {
        private val requiredKeys = setOf(ConfigKeys.MESSAGING_CONFIG, ConfigKeys.REST_CONFIG)
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        private const val SENDER = "SENDER"
        private const val CONFIG_HANDLE = "CONFIG_HANDLE"
        private const val VIRTUAL_NODE_ASYNC_OPERATION_CLIENT_ID = "VIRTUAL_NODE_ASYNC_OPERATION_CLIENT"
    }

    // RestResource values
    override val targetInterface: Class<VirtualNodeRestResource> = VirtualNodeRestResource::class.java
    override val protocolVersion get() = platformInfoProvider.localWorkerPlatformVersion

    // Lifecycle
    private val dependentComponents = DependentComponents.of(
        ::configurationReadService,
        ::virtualNodeInfoReadService,
        ::cpiInfoReadService,
    )

    private val lifecycleCoordinator = coordinatorFactory.createCoordinator(
        LifecycleCoordinatorName.forComponent<VirtualNodeRestResource>()
    ) { event: LifecycleEvent, coordinator: LifecycleCoordinator ->
        when (event) {
            is StartEvent -> {
                dependentComponents.registerAndStartAll(coordinator)
                coordinator.updateStatus(LifecycleStatus.UP, "StartEvent")
            }

            is StopEvent -> coordinator.updateStatus(LifecycleStatus.DOWN,
                "StopEvent - error = ${event.errored}"
            )
            is RegistrationStatusChangeEvent -> {
                when (event.status) {
                    LifecycleStatus.ERROR -> {
                        coordinator.closeManagedResources(setOf(CONFIG_HANDLE))
                        coordinator.postEvent(StopEvent(errored = true))
                    }

                    LifecycleStatus.UP -> {
                        // Receive updates to the REST and Messaging config
                        coordinator.createManagedResource(CONFIG_HANDLE) {
                            configurationReadService.registerComponentForUpdates(
                                coordinator,
                                requiredKeys
                            )
                        }
                    }

                    else -> logger.debug { "Unexpected status: ${event.status}" }
                }
                coordinator.updateStatus(event.status, "RegistrationStatusChangeEvent")
            }

            is ConfigChangedEvent -> {
                if (requiredKeys.all { it in event.config.keys } and event.keys.any { it in requiredKeys }) {
                    val restConfig = event.config.getConfig(ConfigKeys.REST_CONFIG)
                    val messagingConfig = event.config.getConfig(ConfigKeys.MESSAGING_CONFIG)
                    val duration =
                        Duration.ofMillis(restConfig.getInt(ConfigKeys.REST_ENDPOINT_TIMEOUT_MILLIS).toLong())
                    // Make sender unavailable while we're updating
                    coordinator.updateStatus(LifecycleStatus.DOWN, "ConfigChangedEvent")
                    coordinator.createManagedResource(SENDER) {
                        virtualNodeSenderFactory.createSender(
                            duration,
                            messagingConfig,
                            PublisherConfig(VIRTUAL_NODE_ASYNC_OPERATION_CLIENT_ID)
                        )
                    }
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

        check(sender != null) {
            "Sender not initialized, check component status for ${this.javaClass.name}"
        }

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
        return VirtualNodes(virtualNodeInfoReadService.getAll().map(messageConverter::convert))
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

        return messageConverter.convert(virtualNode)
    }

    @Deprecated("Deprecated in favour of upgradeVirtualNode")
    override fun upgradeVirtualNodeDeprecated(
        virtualNodeShortId: String,
        targetCpiFileChecksum: String
    ): ResponseEntity<AsyncResponse> {
        "Deprecated, please use next version where forceUpgrade is passed as a query parameter.".let { msg ->
            logger.warn(msg)
            return ResponseEntity.acceptedButDeprecated(doUpgradeVirtualNode(virtualNodeShortId, targetCpiFileChecksum, false), msg)
        }
    }

    override fun upgradeVirtualNode(
        virtualNodeShortId: String,
        targetCpiFileChecksum: String,
        forceUpgrade: Boolean
    ): ResponseEntity<AsyncResponse> {
        return ResponseEntity.accepted(doUpgradeVirtualNode(virtualNodeShortId, targetCpiFileChecksum, forceUpgrade))
    }

    private fun doUpgradeVirtualNode(
        virtualNodeShortId: String,
        targetCpiFileChecksum: String,
        forceUpgrade: Boolean
    ): AsyncResponse {
        val currentVirtualNode = virtualNodeValidationService.validateAndGetVirtualNode(virtualNodeShortId)
        val currentCpi = requireNotNull(cpiInfoReadService.get(currentVirtualNode.cpiIdentifier)) {
            "Current CPI ${currentVirtualNode.cpiIdentifier} associated with virtual node $virtualNodeShortId was not found."
        }

        if (currentCpi.fileChecksum.toHexString().slice(targetCpiFileChecksum.indices) == targetCpiFileChecksum) {
            throw InvalidStateChangeException(
                "Virtual Node with shorthash $virtualNodeShortId already has " +
                    "CPI with file checksum $targetCpiFileChecksum"
            )
        }

        val targetCpi = virtualNodeValidationService.validateAndGetCpiByChecksum(targetCpiFileChecksum)
        virtualNodeValidationService.validateCpiUpgradePrerequisites(currentCpi, targetCpi)

        val requestId = tryWithExceptionHandling(logger, "Upgrade vNode") {
            sendAsynchronousRequest(
                Instant.now(),
                virtualNodeShortId,
                currentCpi.fileChecksum.toHexString(),
                targetCpi.fileChecksum.toHexString(),
                restContextProvider.principal,
                forceUpgrade
            )
        }

        return AsyncResponse(requestId)
    }

    override fun getVirtualNodeOperationStatus(requestId: String): AsyncOperationStatus {
        val instant = clock.instant()

        // Send request for update to kafka, processed by the db worker in VirtualNodeWriterProcessor
        val rpcRequest = VirtualNodeManagementRequest(
            instant,
            VirtualNodeOperationStatusRequest(requestId)
        )

        // Actually send request and await response message on bus
        val resp = sendAndReceive(rpcRequest)

        return when (val resolvedResponse = resp.responseType) {
            is VirtualNodeCreateStatusResponse -> messageConverter.convert(
                resolvedResponse.virtualNodeOperationStatus,
                OperationTypes.CREATE_VIRTUAL_NODE.toString(),
                requestId
            )
            // It's a connection string change
            is VirtualNodeUpdateDbStatusResponse -> messageConverter.convert(
                resolvedResponse.virtualNodeOperationStatus,
                OperationTypes.CHANGE_VIRTUAL_NODE_DB.toString(),
                null
            )
            is VirtualNodeOperationStatusResponse -> messageConverter.convert(
                resolvedResponse.operationHistory.first(),
                OperationTypes.UPGRADE_VIRTUAL_NODE.toString(),
                null
            )
            is VirtualNodeManagementResponseFailure -> throw handleFailure(resolvedResponse.exception)
            else -> throw UnknownResponseTypeException(resp.responseType::class.java.name)
        }
    }

    override fun getCreateCryptoSchemaSQL(): String {
        return getSchemaSql(DbTypes.CRYPTO, null, null)
    }

    override fun getCreateUniquenessSchemaSQL(): String {
        return getSchemaSql(DbTypes.UNIQUENESS, null, null)
    }

    override fun getCreateVaultSchemaSQL(cpiChecksum: String): String {
        return getSchemaSql(DbTypes.VAULT, null, cpiChecksum)
    }

    override fun getUpdateSchemaSQL(virtualNodeShortId: String, newCpiChecksum: String): String {
        return getSchemaSql(DbTypes.VAULT, virtualNodeShortId, newCpiChecksum)
    }

    private fun getSchemaSql(
        dbType: DbTypes,
        virtualNodeShortId: String?,
        cpiChecksum: String?
    ): String {
        val instant = clock.instant()

        val managementRequest = VirtualNodeManagementRequest(
            instant,
            VirtualNodeSchemaRequest(
                dbType,
                virtualNodeShortId,
                cpiChecksum
            )
        )

        val operationLog = when (dbType) {
            DbTypes.CRYPTO -> "get Schema SQL to create Crypto DB"
            DbTypes.UNIQUENESS -> "get Schema SQL to create Uniqueness DB"
            DbTypes.VAULT -> {
                if (virtualNodeShortId.isNullOrBlank()) {
                    "get Schema SQL to create Vault DB and CPI"
                } else {
                    "get Schema SQL to update CPI"
                }
            }
        }

        // Send request and await response message on bus
        val resp = tryWithExceptionHandling(logger, operationLog) {
            sendAndReceive(managementRequest)
        }

        return when (val resolvedResponse = resp.responseType) {
            is VirtualNodeSchemaResponse -> {
                resolvedResponse.schemaSql
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
        actor: String,
        forceUpgrade: Boolean
    ): String {
        val requestId = generateUpgradeRequestId(virtualNodeShortId, currentCpiFileChecksum, targetCpiFileChecksum)

        sendAsync(
            virtualNodeShortId,
            VirtualNodeAsynchronousRequest(
                requestTime,
                requestId,
                VirtualNodeUpgradeRequest(virtualNodeShortId, targetCpiFileChecksum, actor, forceUpgrade)
            )
        )

        return requestId
    }

    /**
     * Virtual node upgrade request ID deterministically generated using the virtual node identifier, current CPI file
     * checksum and target CPI file checksum. This provides a level of idempotency preventing the same upgrade from
     * triggering more than once.
     */
    private fun generateUpgradeRequestId(
        virtualNodeShortId: String,
        currentCpiFileChecksum: String,
        targetCpiFileChecksum: String
    ): String {
        return virtualNodeShortId.take(12) + currentCpiFileChecksum.take(12) + targetCpiFileChecksum.take(12)
    }

    private fun sendAsync(key: String, request: VirtualNodeAsynchronousRequest) {
        val sender = lifecycleCoordinator.getManagedResource<VirtualNodeSender>(SENDER)
        check(sender != null) {
            "Sender not initialized, check component status for ${this.javaClass.name}"
        }

        return sender.sendAsync(key, request)
    }

    /**
     * Publishes a virtual node create request onto the message bus.
     *
     * @property CreateVirtualNodeRequest contains the data we want to use to construct our virtual node
     * @throws InvalidInputDataException if the request in invalid.
     * @throws InternalServerException if the requested CPI has invalid metadata.
     * @throws ServiceUnavailableException is thrown if the component isn't running.
     * @return [ResponseEntity] containing the request ID for the create virtual node request.
     */
    @Deprecated("Deprecated in favour of `createVirtualNode()`")
    override fun createVirtualNodeDeprecated(request: CreateVirtualNodeRequest): ResponseEntity<AsyncResponse> {
        val groupId = virtualNodeValidationService.validateAndGetGroupId(request)

        val holdingIdentity = requestFactory.createHoldingIdentity(groupId, request)

        virtualNodeValidationService.validateVirtualNodeDoesNotExist(holdingIdentity)

        val asyncRequest = requestFactory.createVirtualNodeRequest(holdingIdentity, request)

        sendAsync(asyncRequest.requestId, asyncRequest)

        "Deprecated, please use next version where non-escaped JSON strings can be passed in the body parameter.".let { msg ->
            logger.warn(msg)
            return ResponseEntity.acceptedButDeprecated(AsyncResponse(asyncRequest.requestId), msg)
        }
    }

    /**
     * Publishes a virtual node create request onto the message bus.
     *
     * @property JsonCreateVirtualNodeRequest contains the data we want to use to construct our virtual node
     * @throws InvalidInputDataException if the request in invalid.
     * @throws InternalServerException if the requested CPI has invalid metadata.
     * @throws ServiceUnavailableException is thrown if the component isn't running.
     * @return [ResponseEntity] containing the request ID for the create virtual node request.
     */
    override fun createVirtualNode(request: JsonCreateVirtualNodeRequest): ResponseEntity<AsyncResponse> {
        val groupId = virtualNodeValidationService.validateAndGetGroupId(request)

        val holdingIdentity = requestFactory.createHoldingIdentity(groupId, request)

        virtualNodeValidationService.validateVirtualNodeDoesNotExist(holdingIdentity)

        val asyncRequest = requestFactory.createVirtualNodeRequest(holdingIdentity, request)

        sendAsync(asyncRequest.requestId, asyncRequest)

        return ResponseEntity.accepted(AsyncResponse(asyncRequest.requestId))
    }

    override fun updateVirtualNodeDb(
        virtualNodeShortId: String,
        request: UpdateVirtualNodeDbRequest
    ): ResponseEntity<AsyncResponse> {
        // Check vnode exists
        val virtualNode = virtualNodeInfoReadService.getByHoldingIdentityShortHash(ShortHash.parse(virtualNodeShortId))
            ?: throw ResourceNotFoundException("Virtual node not found")

        // Log user making change
        logger.debug {
            // Lookup actor to keep track of which REST user triggered an update
            val instant = clock.instant()
            val actor = restContextProvider.principal
            "Received request to update vnode ${virtualNode.holdingIdentity.shortHash} connection strings by $actor at $instant"
        }

        // Build and send change request
        val asyncRequest = requestFactory.updateVirtualNodeDbRequest(virtualNode.holdingIdentity, request)

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
        // Lookup actor to keep track of which REST user triggered an update
        val actor = restContextProvider.principal
        logger.debug { "Received request to update state for $virtualNodeShortId to $newState by $actor at $instant" }

        val virtualNodeState = when (
            validateStateChange(virtualNodeShortId, newState)
        ) {
            VirtualNodeStateTransitions.ACTIVE -> VirtualNodeOperationalState.ACTIVE
            VirtualNodeStateTransitions.MAINTENANCE -> VirtualNodeOperationalState.INACTIVE
        }
        // Send request for update to kafka, precessed by the db worker in VirtualNodeWriterProcessor
        val rpcRequest = VirtualNodeManagementRequest(
            instant,
            VirtualNodeStateChangeRequest(
                virtualNodeShortId,
                virtualNodeState,
                actor
            )
        )
        // Actually send request and await response message on bus
        val resp = tryWithExceptionHandling(logger, "Update vNode state") {
            sendAndReceive(rpcRequest)
        }
        logger.info("Received response to update for $virtualNodeShortId to $newState by $actor")

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

    @Suppress("SwallowedException")
    private fun validateStateChange(virtualNodeShortId: String, newState: String): VirtualNodeStateTransitions {
        val state = try {
            VirtualNodeStateTransitions.valueOf(newState.uppercase())
        } catch (e: IllegalArgumentException) {
            throw InvalidInputDataException(details = mapOf("newState" to "must be one of ACTIVE, MAINTENANCE"))
        }
        val virtualNode = getVirtualNode(virtualNodeShortId)

        if (state == VirtualNodeStateTransitions.ACTIVE && virtualNode.operationInProgress != null) {
            throw BadRequestException(
                "The Virtual Node with shortHash ${virtualNode.holdingIdentity.shortHash} " +
                    "has an operation in progress and cannot be set to Active"
            )
        }

        return state
    }

    private fun handleFailure(exception: ExceptionEnvelope?): java.lang.Exception {
        if (exception == null) {
            logger.warn("Configuration Management request was unsuccessful but no exception was provided.")
            return InternalServerException("Request was unsuccessful but no exception was provided.")
        }
        logger.warn(
            "Remote request failed with exception of type ${exception.errorType}: ${exception.errorMessage}"
        )
        return when (exception.errorType) {
            VirtualNodeOperationNotFoundException::class.java.name -> ResourceNotFoundException(exception.errorMessage)
            VirtualNodeOperationBadRequestException::class.java.name,
            LiquibaseDiffCheckFailedException::class.java.name,
            javax.persistence.RollbackException::class.java.name -> BadRequestException(exception.errorMessage)
            else -> InternalServerException(exception.errorMessage)
        }
    }

    // Mandatory lifecycle methods - def to coordinator
    override val isRunning get() = lifecycleCoordinator.isRunning
    override fun start() = lifecycleCoordinator.start()
    override fun stop() = lifecycleCoordinator.stop()

    private fun createVirtualNodeOperationStatus(requestId: String): VirtualNodeOperationStatus {
        val now = Instant.now()
        return VirtualNodeOperationStatus.newBuilder()
            .setRequestId(requestId)
            .setRequestData("{}")
            .setRequestTimestamp(now)
            .setLatestUpdateTimestamp(now)
            .setHeartbeatTimestamp(null)
            .setState(AsyncOperationState.ACCEPTED.name)
            .setErrors(null)
            .build()
    }
}
