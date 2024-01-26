package net.corda.ledger.persistence.processor

import net.corda.crypto.core.parseSecureHash
import net.corda.data.flow.event.FlowEvent
import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.data.ledger.persistence.PersistTransaction
import net.corda.data.persistence.FindWithNamedQuery
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
import net.corda.utilities.translateFlowContextToMDC
import net.corda.utilities.withMDC
import net.corda.v5.application.flows.FlowContextPropertyKeys.CPK_FILE_CHECKSUM
import net.corda.v5.base.util.ByteArrays
import net.corda.virtualnode.toCorda
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Duration
import java.util.Base64

/**
 * Handles incoming requests, typically from the flow worker, and sends responses.
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

//    var i = 0

    override fun process(request: LedgerPersistenceRequest): FlowEvent {
        val startTime = System.nanoTime()
        val clientRequestId =
            request.flowExternalEventContext.contextProperties.toMap()[MDC_CLIENT_ID] ?: ""
        val holdingIdentity = request.holdingIdentity.toCorda()
        val requestId = request.flowExternalEventContext.requestId

//        log.info("LEDGER REQUEST! type = ${request.request::class.java} - $request")
//
//        when (val r = request.request) {
//            is PersistTransaction -> {
////                log.info("REQUEST BYTES ${Base64.getEncoder().encodeToString(r.transaction.array())}")
////                log.info("REQUEST BYTES ${ByteArrays.toHexString(r.transaction.array())}")
//                val dir = File("/Users/dan.newton/development/tmp/")
//                if (!dir.exists()) {
//                    dir.mkdir()
//                }
//                File("/Users/dan.newton/development/tmp/transaction-${i++}.txt").let {
//                    it.createNewFile()
//                    it.writeBytes(r.transaction.array())
//                }
//            }
//            is FindWithNamedQuery -> {
////                log.info("REQUEST BYTES ${Base64.getEncoder().encodeToString(r.transaction.array())}")
////                log.info("REQUEST BYTES ${ByteArrays.toHexString(r.transaction.array())}")
//                val dir = File("/Users/dan.newton/development/tmp/")
//                if (!dir.exists()) {
//                    dir.mkdir()
//                }
//                r.parameters.map { (key, value) ->
//                    File("/Users/dan.newton/development/tmp/named-query-parameter-$key-$i.txt").let {
//                        it.createNewFile()
//                        it.writeBytes(value.array())
//                    }
//                }
//                i++
//            }
//        }

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
