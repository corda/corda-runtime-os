package net.corda.entityprocessor.impl.internal

import net.corda.crypto.core.parseSecureHash
import net.corda.data.KeyValuePairList
import net.corda.v5.application.flows.FlowContextPropertyKeys.CPK_FILE_CHECKSUM
import net.corda.data.flow.event.FlowEvent
import net.corda.data.persistence.DeleteEntities
import net.corda.data.persistence.DeleteEntitiesById
import net.corda.data.persistence.EntityRequest
import net.corda.data.persistence.EntityResponse
import net.corda.data.persistence.FindAll
import net.corda.data.persistence.FindEntities
import net.corda.data.persistence.FindWithNamedQuery
import net.corda.data.persistence.MergeEntities
import net.corda.data.persistence.PersistEntities
import net.corda.flow.utils.toMap
import net.corda.libs.virtualnode.datamodel.repository.RequestsIdsRepository
import net.corda.libs.virtualnode.datamodel.repository.RequestsIdsRepositoryImpl
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.metrics.CordaMetrics
import net.corda.orm.utils.transaction
import net.corda.persistence.common.EntitySandboxService
import net.corda.persistence.common.ResponseFactory
import net.corda.persistence.common.getEntityManagerFactory
import net.corda.persistence.common.getSerializationService
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.tracing.traceEventProcessingNullableSingle
import net.corda.utilities.MDC_CLIENT_ID
import net.corda.utilities.MDC_EXTERNAL_EVENT_ID
import net.corda.utilities.debug
import net.corda.utilities.translateFlowContextToMDC
import net.corda.utilities.withMDC
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.PersistenceException

fun SandboxGroupContext.getClass(fullyQualifiedClassName: String) =
    this.sandboxGroup.loadClassFromMainBundles(fullyQualifiedClassName)

/**
 * Handles incoming requests, typically from the flow worker, and sends responses.
 *
 * The [EntityRequest] contains the request and a typed payload.
 *
 * The [EntityResponse] contains the response or an exception-like payload whose presence indicates
 * an error has occurred.
 *
 * [payloadCheck] is called against each AMQP payload in the result (not the entire Avro array of results)
 */
class EntityMessageProcessor(
    private val currentSandboxGroupContext: CurrentSandboxGroupContext,
    private val entitySandboxService: EntitySandboxService,
    private val responseFactory: ResponseFactory,
    private val payloadCheck: (bytes: ByteBuffer) -> ByteBuffer,
    private val requestsIdsRepository: RequestsIdsRepository = RequestsIdsRepositoryImpl()
) : DurableProcessor<String, EntityRequest> {
    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val keyClass = String::class.java

    override val valueClass = EntityRequest::class.java

    override fun onNext(events: List<Record<String, EntityRequest>>): List<Record<*, *>> {
        logger.debug { "onNext processing messages ${events.joinToString(",") { it.key }}" }

        return events.mapNotNull { event ->
            val request = event.value
            if (request == null) {
                // We received a [null] external event therefore we do not know the flow id to respond to.
                null
            } else {
                val eventType = request.request?.javaClass?.simpleName ?: "Unknown"
                traceEventProcessingNullableSingle(event, "Crypto Event - $eventType") {
                    CordaMetrics.Metric.Db.EntityPersistenceRequestLag.builder()
                        .withTag(CordaMetrics.Tag.OperationName, request.request::class.java.name)
                        .build()
                        .record(
                            Duration.ofMillis(Instant.now().toEpochMilli() - event.timestamp)
                        )
                    processEvent(request)
                }
            }
        }
    }

    private fun processEvent(request: EntityRequest): Record<*, *> {
        val clientRequestId =
            request.flowExternalEventContext.contextProperties.toMap()[MDC_CLIENT_ID] ?: ""

        return withMDC(
            mapOf(
                MDC_CLIENT_ID to clientRequestId,
                MDC_EXTERNAL_EVENT_ID to request.flowExternalEventContext.requestId
            ) + translateFlowContextToMDC(request.flowExternalEventContext.contextProperties.toMap())
        ) {
            val startTime = System.nanoTime()
            var requestOutcome = "FAILED"
            try {
                val holdingIdentity = request.holdingIdentity.toCorda()
                logger.info("Handling ${request.request::class.java.name} for holdingIdentity ${holdingIdentity.shortHash.value}")

                val cpkFileHashes = request.flowExternalEventContext.contextProperties.items
                    .filter { it.key.startsWith(CPK_FILE_CHECKSUM) }
                    .map { it.value.toSecureHash() }
                    .toSet()

                val sandbox = entitySandboxService.get(holdingIdentity, cpkFileHashes)

                currentSandboxGroupContext.set(sandbox)

                processRequestWithSandbox(sandbox, request).also { requestOutcome = "SUCCEEDED" }
            } catch (e: Exception) {
                responseFactory.errorResponse(request.flowExternalEventContext, e)
            } finally {
                CordaMetrics.Metric.Db.EntityPersistenceRequestTime.builder()
                    .withTag(CordaMetrics.Tag.OperationName, request.request::class.java.name)
                    .withTag(CordaMetrics.Tag.OperationStatus, requestOutcome)
                    .build()
                    .record(Duration.ofNanos(System.nanoTime() - startTime))

                currentSandboxGroupContext.remove()
            }
        }
    }

    @Suppress("ComplexMethod")
    private fun processRequestWithSandbox(
        sandbox: SandboxGroupContext,
        request: EntityRequest
    ): Record<String, FlowEvent> {
        // get the per-sandbox entity manager and serialization services
        val entityManagerFactory = sandbox.getEntityManagerFactory()
        val serializationService = sandbox.getSerializationService()

        val persistenceServiceInternal = PersistenceServiceInternal(sandbox::getClass, payloadCheck)

        val em = entityManagerFactory.createEntityManager()
        return when (val entityRequest = request.request) {
            is PersistEntities -> {
                val requestId = UUID.fromString(request.flowExternalEventContext.requestId)
                val entityResponse = withDeduplicationCheck(
                    requestId,
                    em,
                    onDuplication = {
                        EntityResponse(emptyList(), KeyValuePairList(emptyList()), null)
                    }
                ) {
                    persistenceServiceInternal.persist(serializationService, it, entityRequest)
                }

                responseFactory.successResponse(
                    request.flowExternalEventContext,
                    entityResponse
                )
            }

            is DeleteEntities -> em.transaction {
                responseFactory.successResponse(
                    request.flowExternalEventContext,
                    persistenceServiceInternal.deleteEntities(serializationService, it, entityRequest)
                )
            }

            is DeleteEntitiesById -> em.transaction {
                responseFactory.successResponse(
                    request.flowExternalEventContext,
                    persistenceServiceInternal.deleteEntitiesByIds(
                        serializationService,
                        it,
                        entityRequest
                    )
                )
            }

            is MergeEntities -> {
                val entityResponse = em.transaction {
                    persistenceServiceInternal.merge(serializationService, it, entityRequest)
                }
                responseFactory.successResponse(
                    request.flowExternalEventContext,
                    entityResponse
                )
            }

            is FindEntities -> em.transaction {
                responseFactory.successResponse(
                    request.flowExternalEventContext,
                    persistenceServiceInternal.find(serializationService, it, entityRequest)
                )
            }

            is FindAll -> em.transaction {
                responseFactory.successResponse(
                    request.flowExternalEventContext,
                    persistenceServiceInternal.findAll(serializationService, it, entityRequest)
                )
            }

            is FindWithNamedQuery -> em.transaction {
                responseFactory.successResponse(
                    request.flowExternalEventContext,
                    persistenceServiceInternal.findWithNamedQuery(serializationService, it, entityRequest)
                )
            }

            else -> {
                responseFactory.fatalErrorResponse(
                    request.flowExternalEventContext,
                    CordaRuntimeException("Unknown command")
                )
            }
        }
    }

    private fun String.toSecureHash() = parseSecureHash(this)

    // We should require requestId to be a UUID to avoid request ids collisions
    private fun withDeduplicationCheck(
        requestId: UUID,
        em: EntityManager,
        onDuplication: () -> EntityResponse,
        block: (EntityManager) -> EntityResponse,
    ): EntityResponse {
        return em.transaction {
            try {
                requestsIdsRepository.persist(requestId, it)
                it.flush()
            } catch (e: PersistenceException) {
                // A persistence exception thrown in the de-duplication check means we have already performed the operation and
                // can therefore treat the request as successful
                it.transaction.setRollbackOnly()
                return@transaction onDuplication()
            }
            block(em)
        }
    }
}
