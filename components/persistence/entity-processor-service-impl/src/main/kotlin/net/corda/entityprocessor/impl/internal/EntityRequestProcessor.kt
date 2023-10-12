package net.corda.entityprocessor.impl.internal

import net.corda.data.persistence.EntityRequest
import net.corda.data.persistence.EntityResponse
import net.corda.libs.virtualnode.datamodel.repository.RequestsIdsRepository
import net.corda.libs.virtualnode.datamodel.repository.RequestsIdsRepositoryImpl
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.metrics.CordaMetrics
import net.corda.persistence.common.EntitySandboxService
import net.corda.persistence.common.ResponseFactory
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.tracing.traceEventProcessingNullableSingle
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.Duration
import java.time.Instant


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
class EntityRequestProcessor(
    private val currentSandboxGroupContext: CurrentSandboxGroupContext,
    private val entitySandboxService: EntitySandboxService,
    private val responseFactory: ResponseFactory,
    private val payloadCheck: (bytes: ByteBuffer) -> ByteBuffer,
    private val requestsIdsRepository: RequestsIdsRepository = RequestsIdsRepositoryImpl()
) : DurableProcessor<String, EntityRequest> {

    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        val processorService = ProcessorService()
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
                        .withTag(CordaMetrics.Tag.OperationName, request.request::class.java.name).build().record(
                            Duration.ofMillis(Instant.now().toEpochMilli() - event.timestamp)
                        )

                    // val persistenceServiceInternal = PersistenceServiceInternal(sandbox::getClass, payloadCheck)

                    processorService.processEvent(
                        logger,
                        request,
                        entitySandboxService,
                        currentSandboxGroupContext,
                        responseFactory,
                        requestsIdsRepository,
                        payloadCheck
                    )
                }
            }
        }
    }
}
