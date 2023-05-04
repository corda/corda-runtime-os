package net.corda.membership.impl.persistence.service

import net.corda.data.membership.db.request.MembershipPersistenceRequest
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.response.MembershipPersistenceResponse
import net.corda.data.membership.db.response.MembershipResponseContext
import net.corda.data.membership.db.response.query.ErrorKind
import net.corda.data.membership.db.response.query.PersistenceFailedResponse
import net.corda.membership.impl.persistence.service.handler.HandlerFactories
import net.corda.membership.lib.MessagesHeaders
import net.corda.membership.lib.exceptions.InvalidEntityUpdateException
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.MEMBERSHIP_DB_RPC_RESPONSE_TOPIC
import org.slf4j.LoggerFactory

internal class MembershipPersistenceRPCProcessor(
    private val handlerFactories: HandlerFactories,
) : DurableProcessor<String, MembershipPersistenceRequest> {

    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun onNext(
        events: List<Record<String, MembershipPersistenceRequest>>,
    ): List<Record<*, *>> {
        return events.flatMap { requestRecord ->
            val request = requestRecord.value
            val senderId = requestRecord.headers.firstOrNull {
                it.first == MessagesHeaders.SENDER_ID
            }?.second
            if ((request == null) || (senderId == null)) {
                emptyList()
            } else {
                val response = handleEvent(request)
                listOf(
                    Record(
                        MEMBERSHIP_DB_RPC_RESPONSE_TOPIC,
                        requestRecord.key,
                        response,
                        listOf(
                            MessagesHeaders.SENDER_ID to senderId,
                        ),
                    ),
                )
            }
        }
    }

    private fun handleEvent(request: MembershipPersistenceRequest): MembershipPersistenceResponse {
        logger.info("Received membership persistence request: ${request.request::class.java}")
        val result = try {
            val result = handlerFactories.handle(
                request,
            )
            if (result is Unit) {
                null
            } else {
                result
            }
        } catch (e: Exception) {
            val error = "Exception thrown while processing membership persistence request: ${e.message}"
            logger.warn(error)
            val kind = when (e) {
                is InvalidEntityUpdateException -> ErrorKind.INVALID_ENTITY_UPDATE
                else -> ErrorKind.GENERAL
            }
            PersistenceFailedResponse(error, kind)
        }
        return MembershipPersistenceResponse(
            buildResponseContext(request.context),
            result,
        )
    }

    private fun buildResponseContext(requestContext: MembershipRequestContext): MembershipResponseContext {
        return with(requestContext) {
            MembershipResponseContext(
                requestTimestamp,
                requestId,
                handlerFactories.persistenceHandlerServices.clock.instant(),
                holdingIdentity,
            )
        }
    }

    override val keyClass = String::class.java
    override val valueClass = MembershipPersistenceRequest::class.java
}
