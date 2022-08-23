package net.corda.processors.ledger

import java.nio.ByteBuffer
import javax.persistence.EntityManagerFactory
import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.FlowEvent
import net.corda.data.ledger.consensual.PersistTransaction
import net.corda.data.persistence.ConsensualLedgerRequest
import net.corda.data.persistence.EntityResponse
import net.corda.data.persistence.EntityResponseFailure
import net.corda.data.persistence.Error
import net.corda.entityprocessor.impl.internal.EntitySandboxContextTypes
import net.corda.entityprocessor.impl.internal.EntitySandboxService
import net.corda.entityprocessor.impl.internal.exceptions.KafkaMessageSizeException
import net.corda.entityprocessor.impl.internal.exceptions.NotReadyException
import net.corda.entityprocessor.impl.internal.exceptions.NullParameterException
import net.corda.entityprocessor.impl.internal.exceptions.VirtualNodeException
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.orm.utils.transaction
import net.corda.processors.ledger.impl.ConsensualLedgerDAO
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.schema.Schemas
import net.corda.utilities.time.Clock
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda


fun EntitySandboxService.getClass(holdingIdentity: HoldingIdentity, fullyQualifiedClassName: String) =
    this.get(holdingIdentity).sandboxGroup.loadClassFromMainBundles(fullyQualifiedClassName)

/**
 * Handles incoming requests, typically from the flow worker, and sends responses.
 *
 * The [ConsensualLedgerRequest] contains the request and a typed payload.
 *
 * The [EntityResponse] contains the response or an exception-like payload whose presence indicates
 * an error has occurred.
 */
class ConsensualLedgerProcessor(
    private val entitySandboxService: EntitySandboxService,
    private val clock: Clock,
    private val payloadCheck: (bytes: ByteBuffer) -> ByteBuffer,
) : DurableProcessor<String, ConsensualLedgerRequest> {
    companion object {
        private val log = contextLogger()
    }

    private fun SandboxGroupContext.getSerializationService(): SerializationService =
        getObjectByKey(EntitySandboxContextTypes.SANDBOX_SERIALIZER)
            ?: throw CordaRuntimeException(
                "Entity serialization service not found within the sandbox for identity: " +
                        "${virtualNodeContext.holdingIdentity}"
            )

    private fun SandboxGroupContext.getEntityManagerFactory(): EntityManagerFactory =
        getObjectByKey(EntitySandboxContextTypes.SANDBOX_EMF)
            ?: throw CordaRuntimeException(
                "Entity manager factory not found within the sandbox for identity: " +
                        "${virtualNodeContext.holdingIdentity}"
            )

    override fun onNext(events: List<Record<String, ConsensualLedgerRequest>>): List<Record<*, *>> {
        log.debug("onNext processing messages ${events.joinToString(",") { it.key }}")
        val responses = mutableListOf<Record<String, FlowEvent>>()
        events.forEach {
            val response = try {
                processRequest(it.key, it.value!!)
            } catch (e: Exception) {
                // If we're catching at this point, it's an unrecoverable error.
                failureResponse(it.key, e, Error.FATAL)
            }
            val flowId = it.value!!.flowId
            responses.add(Record(Schemas.Flow.FLOW_EVENT_TOPIC, flowId, FlowEvent(flowId, response)))
        }

        return responses
    }

    private fun processRequest(requestId: String, request: ConsensualLedgerRequest): EntityResponse {
        val holdingIdentity = request.holdingIdentity.toCorda()

        // Get the sandbox for the given request.
        // Handle any exceptions as close to the throw-site as possible.
        val sandbox = try {
            entitySandboxService.get(holdingIdentity)
        } catch (e: NotReadyException) {
            // Flow worker could retry later, but may not be successful.
            return failureResponse(requestId, e, Error.NOT_READY)
        } catch (e: VirtualNodeException) {
            // Flow worker could retry later, but may not be successful.
            return failureResponse(requestId, e, Error.VIRTUAL_NODE)
        } catch (e: Exception) {
            throw e // rethrow and handle higher up
        }

        return processRequestWithSandbox(sandbox, requestId, request)
    }

    @Suppress("ComplexMethod")
    private fun processRequestWithSandbox(
        sandbox: SandboxGroupContext,
        requestId: String,
        request: ConsensualLedgerRequest
    ): EntityResponse {
        val holdingIdentity = request.holdingIdentity.toCorda()
        log.info("processRequestWithSandbox, request: ${request}, holding identity: ${holdingIdentity}")

        // get the per-sandbox entity manager and serialization services
        val entityManagerFactory = sandbox.getEntityManagerFactory()
        // val serializationService = sandbox.getSerializationService()  // TODO: use
        val consensualLedgerDAO = ConsensualLedgerDAO(requestId, clock::instant, entitySandboxService::getClass)

        // We match on the type, and delegate to the appropriate method in the DAO.
        val response = try {
            entityManagerFactory.createEntityManager().transaction {
                val req = request.request
                when (req) {
                    is PersistTransaction -> consensualLedgerDAO.persistTransaction(req, it)

                    else -> {
                        failureResponse(requestId, CordaRuntimeException("Unknown command"), Error.FATAL)
                    }
                }
            }
        } catch (e: java.io.NotSerializableException) {
            failureResponse(requestId, e, Error.FATAL)
        } catch (e: KafkaMessageSizeException) {
            failureResponse(requestId, e, Error.FATAL)
        } catch (e: NullParameterException) {
            failureResponse(requestId, e, Error.FATAL)
        } catch (e: Exception) {
            failureResponse(requestId, e, Error.DATABASE)
        }

        return response
    }

    private fun failureResponse(requestId: String, e: Exception, errorType: Error): EntityResponse {
        if (errorType == Error.FATAL) {
            log.error("Fatal exception occurred (type=$errorType) for flow-worker request $requestId", e)
        } else {
            log.warn("Exception occurred (type=$errorType) for flow-worker request $requestId", e)
        }

        return EntityResponse(
            clock.instant(),
            requestId,
            EntityResponseFailure(
                errorType,
                ExceptionEnvelope(e::class.java.simpleName, e.localizedMessage)
            )
        )
    }
    override val keyClass: Class<String>
        get() = String::class.java

    override val valueClass: Class<ConsensualLedgerRequest>
        get() = ConsensualLedgerRequest::class.java
}
