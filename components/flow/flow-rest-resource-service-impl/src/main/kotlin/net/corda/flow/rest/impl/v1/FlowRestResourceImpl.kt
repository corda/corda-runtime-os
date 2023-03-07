package net.corda.flow.rest.impl.v1

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.data.virtualnode.VirtualNodeInfo
import net.corda.data.virtualnode.VirtualNodeState
import net.corda.flow.rest.FlowRestResourceServiceException
import net.corda.flow.rest.FlowStatusCacheService
import net.corda.flow.rest.factory.MessageFactory
import net.corda.flow.rest.impl.flowstatus.websocket.WebSocketFlowStatusUpdateListener
import net.corda.flow.rest.v1.FlowRestResource
import net.corda.flow.rest.v1.types.request.StartFlowParameters
import net.corda.flow.rest.v1.types.response.FlowStatusResponse
import net.corda.flow.rest.v1.types.response.FlowStatusResponses
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.publisher.waitOnPublisherFutures
import net.corda.messaging.api.records.Record
import net.corda.permissions.validation.PermissionValidationService
import net.corda.rbac.schema.RbacKeys
import net.corda.rbac.schema.RbacKeys.PREFIX_SEPARATOR
import net.corda.rbac.schema.RbacKeys.START_FLOW_PREFIX
import net.corda.rest.PluggableRestResource
import net.corda.rest.exception.BadRequestException
import net.corda.rest.exception.ForbiddenException
import net.corda.rest.exception.InvalidInputDataException
import net.corda.rest.exception.OperationNotAllowedException
import net.corda.rest.exception.ResourceAlreadyExistsException
import net.corda.rest.exception.ResourceNotFoundException
import net.corda.rest.messagebus.MessageBusUtils.tryWithExceptionHandling
import net.corda.rest.response.ResponseEntity
import net.corda.rest.security.CURRENT_REST_CONTEXT
import net.corda.rest.ws.DuplexChannel
import net.corda.rest.ws.WebSocketValidationException
import net.corda.schema.Schemas.Flow.FLOW_MAPPER_EVENT_TOPIC
import net.corda.schema.Schemas.Flow.FLOW_STATUS_TOPIC
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.read.rest.extensions.getByHoldingIdentityShortHashOrThrow
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

@Suppress("LongParameterList")
@Component(service = [FlowRestResource::class, PluggableRestResource::class], immediate = true)
class FlowRestResourceImpl @Activate constructor(
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = FlowStatusCacheService::class)
    private val flowStatusCacheService: FlowStatusCacheService,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = MessageFactory::class)
    private val messageFactory: MessageFactory,
    @Reference(service = CpiInfoReadService::class)
    private val cpiInfoReadService: CpiInfoReadService,
    @Reference(service = PermissionValidationService::class)
    private val permissionValidationService: PermissionValidationService
) : FlowRestResource, PluggableRestResource<FlowRestResource>, Lifecycle {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val PUBLICATION_TIMEOUT_SECONDS = 30L
    }

    override val isRunning: Boolean get() = publisher != null

    override val targetInterface: Class<FlowRestResource> = FlowRestResource::class.java
    override val protocolVersion: Int = 1

    private var publisher: Publisher? = null
    private var fatalErrorOccurred = false
    private lateinit var onFatalError:() -> Unit

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
                """Supplied Client Request ID "$clientRequestId" is invalid,""" +
                        """ it must conform to the pattern "${RbacKeys.CLIENT_REQ_REGEX}".""")
        }
    }

    override fun startFlow(
        holdingIdentityShortHash: String,
        startFlow: StartFlowParameters
    ): ResponseEntity<FlowStatusResponse> {
        if (publisher == null) {
            throw FlowRestResourceServiceException("FlowRestResource has not been initialised ")
        }
        if (fatalErrorOccurred) {
            // If Kafka has told us this publisher should not attempt a retry, most likely we have already been
            // replaced by another worker and have been "fenced". In that case it would be unsafe to create another
            // producer, because we'd attempt to replace our replacement. Most likely service orchestration has already
            // replaced us - nothing else should lead to us being fenced - and therefore should be responsible for
            // closing us down soon. There are other fatal error types, but none are recoverable by definition.
            throw FlowRestResourceServiceException("Fatal error occurred, can no longer start flows from this worker")
        }

        val vNode = getVirtualNode(holdingIdentityShortHash)

        if (vNode.flowStartOperationalStatus == VirtualNodeState.INACTIVE) {
            throw OperationNotAllowedException("Flow start capabilities of virtual node $holdingIdentityShortHash are not operational.")
        }

        val clientRequestId = startFlow.clientRequestId
        val flowStatus = flowStatusCacheService.getStatus(clientRequestId, vNode.holdingIdentity)

        validateClientRequestId(clientRequestId)

        if (flowStatus != null) {
            throw ResourceAlreadyExistsException("A flow has already been started with for the requested holdingId and clientRequestId")
        }

        val flowClassName = startFlow.flowClassName
        val startableFlows = getStartableFlows(holdingIdentityShortHash, vNode)
        if (!startableFlows.contains(flowClassName)) {
            val cpiMeta = cpiInfoReadService.get(CpiIdentifier.fromAvro(vNode.cpiIdentifier))
            val msg = "The flow that was requested ($flowClassName) is not in the list of startable flows for this holding identity."
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

        val restContext = CURRENT_REST_CONTEXT.get()
        val principal = restContext.principal

        if (!permissionValidationService.permissionValidator.authorizeUser(principal,
                "$START_FLOW_PREFIX$PREFIX_SEPARATOR${startFlow.flowClassName}")) {
            throw ForbiddenException("User $principal is not allowed to start a flow: ${startFlow.flowClassName}")
        }

        // TODO Platform properties to be populated correctly, for now a fixed 'account zero' is the only property
        // This is a placeholder which indicates access to everything, see CORE-6076
        val flowContextPlatformProperties = mapOf("corda.account" to "account-zero")
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
            Record(FLOW_MAPPER_EVENT_TOPIC, status.key.toString(), startEvent),
            Record(FLOW_STATUS_TOPIC, status.key, status),
        )

        val recordFutures = try {
            tryWithExceptionHandling(
                log,
                "Publishing start flow events",
                untranslatedExceptions = setOf(CordaMessageAPIFatalException::class.java)
            ) {
                publisher!!.publish(records)
            }
        } catch (ex: CordaMessageAPIFatalException) {
            throw markFatalAndReturnFailureException(ex)
        }
        waitOnPublisherFutures(recordFutures, PUBLICATION_TIMEOUT_SECONDS, TimeUnit.SECONDS) { ex, failureIsTerminal ->
            if (failureIsTerminal) {
                throw markFatalAndReturnFailureException(ex)
            } else {
                throw failureException(ex)
            }
        }
        return ResponseEntity.accepted(messageFactory.createFlowStatusResponse(status))
    }

    private fun markFatalAndReturnFailureException(exception: Exception): Exception {
        fatalErrorOccurred = true
        log.error("Fatal error occurred, FlowRestResource can no longer start flows, worker expected to terminate.", exception)
        onFatalError()
        return failureException(exception)
    }

    private fun failureException(cause: Exception): Exception =
        FlowRestResourceServiceException("Failed to publish the Start Flow event.", cause)

    private fun getStartableFlows(holdingIdentityShortHash: String, vNode: VirtualNodeInfo): List<String> {
        val cpiMeta = cpiInfoReadService.get(CpiIdentifier.fromAvro(vNode.cpiIdentifier))
            ?: throw ResourceNotFoundException("Failed to find a CPI for ID='${holdingIdentityShortHash}'")
        return cpiMeta.cpksMetadata.flatMap {
            it.cordappManifest.clientStartableFlows
        }
    }

    override fun getFlowStatus(holdingIdentityShortHash: String, clientRequestId: String): FlowStatusResponse {
        val vNode = getVirtualNode(holdingIdentityShortHash)

        val flowStatus = flowStatusCacheService.getStatus(clientRequestId, vNode.holdingIdentity)
            ?: throw ResourceNotFoundException(
                "Failed to find the flow status for holding identity='${holdingIdentityShortHash} " +
                        "and Client Request ID='${clientRequestId}"
            )

        return messageFactory.createFlowStatusResponse(flowStatus)
    }

    override fun getMultipleFlowStatus(holdingIdentityShortHash: String): FlowStatusResponses {
        val vNode = getVirtualNode(holdingIdentityShortHash)
        val flowStatuses = flowStatusCacheService.getStatusesPerIdentity(vNode.holdingIdentity)
        return FlowStatusResponses(flowStatusResponses = flowStatuses.map { messageFactory.createFlowStatusResponse(it) })
    }

    override fun registerFlowStatusUpdatesFeed(
        channel: DuplexChannel,
        holdingIdentityShortHash: String,
        clientRequestId: String
    ) {
        val sessionId = channel.id
        val holdingIdentity = try {
            getVirtualNode(holdingIdentityShortHash).holdingIdentity
        } catch (e: BadRequestException) {
            channel.error(WebSocketValidationException(e.message, e))
            return
        } catch (e: ResourceNotFoundException) {
            channel.error(WebSocketValidationException(e.message, e))
            return
        }
        try {
            val flowStatusFeedRegistration = flowStatusCacheService.registerFlowStatusListener(
                clientRequestId,
                holdingIdentity,
                WebSocketFlowStatusUpdateListener(clientRequestId, holdingIdentity, channel)
            )

            channel.onClose = { statusCode, reason ->
                log.info(
                    "Close hook called for duplex channel $sessionId with status $statusCode, reason: $reason " +
                            "(clientRequestId=$clientRequestId, holdingId=$holdingIdentityShortHash)"
                )
                flowStatusFeedRegistration.close()
            }
        } catch (e: WebSocketValidationException) {
            log.warn("Validation error while registering flow status listener - ${e.message ?: "No exception message provided."}")
            error(e)
        } catch (e: Exception) {
            log.error("Unexpected error at registerFlowStatusListener")
            error(e)
        }
    }

    override fun start() = Unit

    override fun stop() {
        publisher?.close()
    }

    private fun getVirtualNode(holdingIdentityShortHash: String): VirtualNodeInfo {
        return virtualNodeInfoReadService.getByHoldingIdentityShortHashOrThrow(holdingIdentityShortHash).toAvro()
    }
}
