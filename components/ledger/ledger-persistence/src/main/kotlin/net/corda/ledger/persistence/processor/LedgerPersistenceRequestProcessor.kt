package net.corda.ledger.persistence.processor

import net.corda.crypto.core.parseSecureHash
import net.corda.data.flow.event.FlowEvent
import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.flow.utils.toMap
import net.corda.ledger.persistence.common.InconsistentLedgerStateException
import net.corda.ledger.persistence.common.UnsupportedLedgerTypeException
import net.corda.ledger.persistence.common.UnsupportedRequestTypeException
import net.corda.messaging.api.processor.SyncRPCProcessor
import net.corda.metrics.CordaMetrics
import net.corda.persistence.common.EntitySandboxService
import net.corda.persistence.common.ResponseFactory
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.utilities.MDC_CLIENT_ID
import net.corda.utilities.MDC_EXTERNAL_EVENT_ID
import net.corda.utilities.MDC_VNODE_ID
import net.corda.utilities.setMDC
import net.corda.utilities.translateFlowContextToMDC
import net.corda.utilities.withMDC
import net.corda.v5.application.flows.FlowContextPropertyKeys.CPK_FILE_CHECKSUM
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * Handles incoming requests, typically from the flow worker, and sends responses.
 * Handling persistence requests involves user-supplied hibernate code, so we run the requests within
 * sandboxes to protect the system from the user code.
 *
 * Takes care of maintaining the managed diagnostic context with the external event ID and request ID
 * so that flow operations can be traced. Tracks the time taken to handle the request.
 */
@Suppress("LongParameterList")
class LedgerPersistenceRequestProcessor(
    private val currentSandboxGroupContext: CurrentSandboxGroupContext,
    private val entitySandboxService: EntitySandboxService,
    private val delegatedRequestHandlerSelector: DelegatedRequestHandlerSelector,
    private val responseFactory: ResponseFactory
) : SyncRPCProcessor<LedgerPersistenceRequest, FlowEvent> {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val requestClass = LedgerPersistenceRequest::class.java
    override val responseClass = FlowEvent::class.java

    override fun process(request: LedgerPersistenceRequest): FlowEvent {
        val startTime = System.nanoTime()
        val clientRequestId =
            request.flowExternalEventContext.contextProperties.toMap()[MDC_CLIENT_ID] ?: ""
        val holdingIdentity = request.holdingIdentity.toCorda()
        val requestId = request.flowExternalEventContext.requestId

        val result =
            withMDC(
                mapOf(
                    MDC_CLIENT_ID to clientRequestId,
                    MDC_EXTERNAL_EVENT_ID to requestId
                ) + translateFlowContextToMDC(request.flowExternalEventContext.contextProperties.toMap())
            ) {
                try {
                    val cpkFileHashes = request.flowExternalEventContext.contextProperties.items
                        .filter { it.key.startsWith(CPK_FILE_CHECKSUM) }
                        .map { it.value.toSecureHash() }
                        .toSet()

                    val sandbox = entitySandboxService.get(holdingIdentity, cpkFileHashes)

                    setMDC(mapOf(MDC_VNODE_ID to sandbox.virtualNodeContext.holdingIdentity.shortHash.toString()))

                    currentSandboxGroupContext.set(sandbox)

                    delegatedRequestHandlerSelector.selectHandler(sandbox, request).execute()
                } catch (e: Exception) {
                    logger.error("${e.message}", e)
                    listOf(
                        when (e) {
                            is UnsupportedLedgerTypeException,
                            is UnsupportedRequestTypeException,
                            is InconsistentLedgerStateException -> {
                                responseFactory.fatalErrorResponse(request.flowExternalEventContext, e)
                            }

                            else -> {
                                responseFactory.errorResponse(request.flowExternalEventContext, e)
                            }
                        }
                    )
                } finally {
                    currentSandboxGroupContext.remove()
                }.also {
                    CordaMetrics.Metric.Ledger.PersistenceExecutionTime
                        .builder()
                        .forVirtualNode(holdingIdentity.shortHash.toString())
                        .withTag(CordaMetrics.Tag.LedgerType, request.ledgerType.toString())
                        .withTag(CordaMetrics.Tag.OperationName, request.request.javaClass.simpleName)
                        .build()
                        .record(Duration.ofNanos(System.nanoTime() - startTime))
                }
            }
        return result.single().value as FlowEvent
    }
}

private fun String.toSecureHash() = parseSecureHash(this)
