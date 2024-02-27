package net.corda.flow.rest.impl.v1

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.data.flow.FlowKey
import net.corda.data.flow.output.FlowStates
import net.corda.data.flow.output.FlowStatus
import net.corda.data.virtualnode.VirtualNodeInfo
import net.corda.data.virtualnode.VirtualNodeOperationalState
import net.corda.flow.rest.FlowStatusLookupService
import net.corda.flow.rest.factory.MessageFactory
import net.corda.flow.rest.impl.FlowRestExceptionConstants
import net.corda.flow.rest.v1.FlowRestResource
import net.corda.flow.rest.v1.types.request.StartFlowParameters
import net.corda.flow.rest.v1.types.response.FlowResultResponse
import net.corda.flow.rest.v1.types.response.FlowStatusResponse
import net.corda.flow.rest.v1.types.response.FlowStatusResponses
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.permissions.validation.PermissionValidationService
import net.corda.rbac.schema.RbacKeys
import net.corda.rbac.schema.RbacKeys.PREFIX_SEPARATOR
import net.corda.rbac.schema.RbacKeys.START_FLOW_PREFIX
import net.corda.rest.PluggableRestResource
import net.corda.rest.exception.BadRequestException
import net.corda.rest.exception.ForbiddenException
import net.corda.rest.exception.InternalServerException
import net.corda.rest.exception.InvalidInputDataException
import net.corda.rest.exception.OperationNotAllowedException
import net.corda.rest.exception.ResourceAlreadyExistsException
import net.corda.rest.exception.ResourceNotFoundException
import net.corda.rest.exception.ServiceUnavailableException
import net.corda.rest.messagebus.MessageBusUtils.tryWithExceptionHandling
import net.corda.rest.response.ResponseEntity
import net.corda.rest.security.CURRENT_REST_CONTEXT
import net.corda.schema.Schemas.Flow.FLOW_MAPPER_START
import net.corda.schema.Schemas.Flow.FLOW_STATUS_TOPIC
import net.corda.tracing.TraceTag
import net.corda.tracing.addTraceContextToRecord
import net.corda.tracing.trace
import net.corda.utilities.MDC_CLIENT_ID
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.read.rest.extensions.getByHoldingIdentityShortHashOrThrow
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Suppress("LongParameterList", "TooManyFunctions")
@Component(service = [FlowRestResource::class, PluggableRestResource::class])
class FlowRestResourceImpl @Activate constructor(
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = FlowStatusLookupService::class)
    private val flowStatusLookupService: FlowStatusLookupService,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = MessageFactory::class)
    private val messageFactory: MessageFactory,
    @Reference(service = CpiInfoReadService::class)
    private val cpiInfoReadService: CpiInfoReadService,
    @Reference(service = PermissionValidationService::class)
    private val permissionValidationService: PermissionValidationService,
    @Reference(service = PlatformInfoProvider::class)
    private val platformInfoProvider: PlatformInfoProvider,
) : FlowRestResource, PluggableRestResource<FlowRestResource>, Lifecycle {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val isRunning: Boolean get() = publisher != null

    override val targetInterface: Class<FlowRestResource> = FlowRestResource::class.java
    override val protocolVersion get() = platformInfoProvider.localWorkerPlatformVersion

    private var publisher: Publisher? = null
    private var fatalErrorOccurred = false
    private lateinit var onFatalError: () -> Unit

    override fun initialise(config: SmartConfig, onFatalError: () -> Unit) {
        this.onFatalError = onFatalError
        publisher?.close()
        publisher = publisherFactory.createPublisher(PublisherConfig("FlowRestResource"), config)
    }

    private fun regexMatch(input: String, regex: String): Boolean {
        return input.matches(regex.toRegex(RegexOption.IGNORE_CASE))
    }

    private fun validateClientRequestId(clientRequestId: String) {
        if (!regexMatch(clientRequestId, RbacKeys.CLIENT_REQ_REGEX)) {
            throw BadRequestException(
                FlowRestExceptionConstants.INVALID_ID.format(
                    clientRequestId, RbacKeys.CLIENT_REQ_REGEX
                )
            )
        }
    }

    override fun startFlow(
        holdingIdentityShortHash: String,
        startFlow: StartFlowParameters
    ): ResponseEntity<FlowStatusResponse> {
        return trace("API - Start Flow") {
            traceVirtualNodeId(holdingIdentityShortHash)

            if (publisher == null) {
                throw ServiceUnavailableException(FlowRestExceptionConstants.UNINITIALIZED_ERROR)
            }
            if (fatalErrorOccurred) {
                // If Kafka has told us this publisher should not attempt a retry, most likely we have already been
                // replaced by another worker and have been "fenced". In that case it would be unsafe to create another
                // producer, because we'd attempt to replace our replacement. Most likely service orchestration has already
                // replaced us - nothing else should lead to us being fenced - and therefore should be responsible for
                // closing us down soon. There are other fatal error types, but none are recoverable by definition.
                throw ServiceUnavailableException(FlowRestExceptionConstants.TEMPORARY_INTERNAL_FAILURE)
            }

            val vNode = getVirtualNode(holdingIdentityShortHash)

            if (vNode.flowStartOperationalStatus == VirtualNodeOperationalState.INACTIVE) {
                throw OperationNotAllowedException(
                    FlowRestExceptionConstants.NOT_OPERATIONAL
                        .format(holdingIdentityShortHash)
                )
            }

            val clientRequestId = startFlow.clientRequestId

            traceRequestId(clientRequestId)

            val flowStatus = flowStatusLookupService.getStatus(clientRequestId, vNode.holdingIdentity)

            validateClientRequestId(clientRequestId)

            if (flowStatus != null) {
                throw ResourceAlreadyExistsException(FlowRestExceptionConstants.ALREADY_EXISTS_ERROR)
            }

            val flowClassName = startFlow.flowClassName
            val startableFlows = getStartableFlows(holdingIdentityShortHash, vNode)
            if (!startableFlows.contains(flowClassName)) {
                val cpiMeta = cpiInfoReadService.get(CpiIdentifier.fromAvro(vNode.cpiIdentifier))
                val msg =
                    "The flow that was requested ($flowClassName) is not in the list of startable flows for this holding identity."
                val details = mapOf(
                    "CPI-name" to cpiMeta?.cpiId?.name.toString(),
                    "CPI-version" to cpiMeta?.cpiId?.version.toString(),
                    "CPI-signer-summary-hash" to cpiMeta?.cpiId?.signerSummaryHash.toString(),
                    "virtual-node" to vNode.holdingIdentity.x500Name,
                    "group-id" to vNode.holdingIdentity.groupId,
                    "startableFlows" to startableFlows.joinToString(",")
                )
                log.info("$msg $details")
                throw InvalidInputDataException(msg, details)
            }

            traceTag(TraceTag.FLOW_CLASS, flowClassName)

            val restContext = CURRENT_REST_CONTEXT.get()
            val principal = restContext.principal

            if (!permissionValidationService.permissionValidator.authorizeUser(
                    principal,
                    "$START_FLOW_PREFIX$PREFIX_SEPARATOR${startFlow.flowClassName}"
                )
            ) {
                throw ForbiddenException(
                    FlowRestExceptionConstants.FORBIDDEN.format(
                        principal,
                        startFlow.flowClassName
                    )
                )
            }

            // TODO Platform properties to be populated correctly.
            // This is a placeholder which indicates access to everything, see CORE-6076
            val flowContextPlatformProperties = mapOf(
                "corda.account" to "account-zero",
                MDC_CLIENT_ID to clientRequestId
            )
            val startEvent =
                messageFactory.createStartFlowEvent(
                    clientRequestId,
                    vNode,
                    flowClassName,
                    startFlow.requestBody.escapedJson,
                    flowContextPlatformProperties
                )
            val status = messageFactory.createStartFlowStatus(clientRequestId, vNode, flowClassName)

            val records = listOf(
                addTraceContextToRecord(
                    Record(
                        FLOW_MAPPER_START,
                        getKeyForStartEvent(status.key, holdingIdentityShortHash), startEvent
                    )
                ),
                Record(FLOW_STATUS_TOPIC, status.key, status),
            )

            val batchFuture = try {
                tryWithExceptionHandling(
                    log,
                    "Publishing start flow events",
                    untranslatedExceptions = setOf(CordaMessageAPIFatalException::class.java)
                ) {
                    publisher!!.batchPublish(records)
                }
            } catch (ex: CordaMessageAPIFatalException) {
                throw markFatalAndReturnFailureException(ex)
            }

            // Do not block REST thread execution till future completes, instead add a hook to log an error if batch
            // publication fails for whatever reason and return to the REST caller that flow start been accepted.
            // Should they wish to check the actual execution progress, they can always check the status using
            // client request id provided.
            batchFuture.exceptionally {
                log.warn(
                    "Failed to publish start flow batch for flowClass: $flowClassName, " +
                            "clientRequestId: $clientRequestId on vNode $holdingIdentityShortHash", it
                )

                if (it is CordaMessageAPIFatalException) {
                    // Note: not throwing returned exception as this call will be performed asynchronously from 
                    // publisher's thread pool, just calling this method to log the fatal error
                    markFatalAndReturnFailureException(it)
                }
            }

            ResponseEntity.accepted(messageFactory.createFlowStatusResponse(status))
        }
    }

    private fun getKeyForStartEvent(flowKey: FlowKey, holdingIdentityShortHash: String): String {
        return "${flowKey.id}-${holdingIdentityShortHash}"
    }

    private fun markFatalAndReturnFailureException(exception: Exception): Exception {
        fatalErrorOccurred = true
        log.error(FlowRestExceptionConstants.FATAL_ERROR, exception)
        onFatalError()
        return InternalServerException(FlowRestExceptionConstants.FATAL_ERROR)
    }

    private fun getStartableFlows(holdingIdentityShortHash: String, vNode: VirtualNodeInfo): List<String> {
        val cpiMeta = cpiInfoReadService.get(CpiIdentifier.fromAvro(vNode.cpiIdentifier))
            ?: throw ResourceNotFoundException(FlowRestExceptionConstants.CPI_NOT_FOUND.format(holdingIdentityShortHash))
        return cpiMeta.cpksMetadata.flatMap {
            it.cordappManifest.clientStartableFlows
        }
    }

    override fun getFlowStatus(holdingIdentityShortHash: String, clientRequestId: String): FlowStatusResponse {
        val vNode = getVirtualNode(holdingIdentityShortHash)
        val flowStatus = flowStatusLookupService.getStatus(clientRequestId, vNode.holdingIdentity)
            ?: throw ResourceNotFoundException(
                FlowRestExceptionConstants.FLOW_STATUS_NOT_FOUND.format(
                    holdingIdentityShortHash, clientRequestId
                )
            )
        return messageFactory.createFlowStatusResponse(flowStatus)
    }

    override fun getMultipleFlowStatus(holdingIdentityShortHash: String): FlowStatusResponses {
        return getMultipleFlowStatus(holdingIdentityShortHash, null)
    }

    override fun getMultipleFlowStatus(holdingIdentityShortHash: String, status: String?): FlowStatusResponses {
        val vNode = getVirtualNode(holdingIdentityShortHash)
        val flowStatuses = flowStatusLookupService.getStatusesPerIdentity(vNode.holdingIdentity)

        val filteredStatuses = status?.let {
            val flowState = try {
                FlowStates.valueOf(it)
            } catch (e: IllegalArgumentException) {
                throw BadRequestException(
                    "Status to filter by is not found in list of valid statuses: ${FlowStates.values()}"
                )
            }
            flowStatuses.filter { statusFilter -> statusFilter.flowStatus == flowState }
        } ?: flowStatuses

        return createFlowStatusResponses(filteredStatuses)
    }

    private fun createFlowStatusResponses(flowStatuses: List<FlowStatus>): FlowStatusResponses {
        return FlowStatusResponses(flowStatusResponses = flowStatuses.map { messageFactory.createFlowStatusResponse(it) })
    }

    override fun getFlowResult(
        holdingIdentityShortHash: String,
        clientRequestId: String
    ): ResponseEntity<FlowResultResponse> {
        val vNode = getVirtualNode(holdingIdentityShortHash)
        val flowStatus = flowStatusLookupService.getStatus(clientRequestId, vNode.holdingIdentity)
            ?: throw ResourceNotFoundException(
                FlowRestExceptionConstants.FLOW_STATUS_NOT_FOUND.format(
                    holdingIdentityShortHash, clientRequestId
                )
            )
        return messageFactory.createFlowResultResponse(flowStatus)
    }

    override fun start() = Unit

    override fun stop() {
        publisher?.close()
    }

    private fun getVirtualNode(holdingIdentityShortHash: String): VirtualNodeInfo {
        return virtualNodeInfoReadService.getByHoldingIdentityShortHashOrThrow(holdingIdentityShortHash).toAvro()
    }
}
