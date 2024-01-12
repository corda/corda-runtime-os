package net.corda.membership.impl.persistence.service

import net.corda.data.membership.db.request.MembershipPersistenceRequest
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.response.MembershipPersistenceResponse
import net.corda.data.membership.db.response.MembershipResponseContext
import net.corda.data.membership.db.response.query.ErrorKind
import net.corda.data.membership.db.response.query.PersistenceFailedResponse
import net.corda.membership.impl.persistence.service.handler.HandlerFactories
import net.corda.membership.lib.exceptions.InvalidEntityUpdateException
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.utilities.trace
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

internal class MembershipPersistenceRPCProcessor(
    private val handlerFactories: HandlerFactories,
) : RPCResponderProcessor<MembershipPersistenceRequest, MembershipPersistenceResponse> {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun onNext(
        request: MembershipPersistenceRequest,
        respFuture: CompletableFuture<MembershipPersistenceResponse>
    ) {
        logger.trace { "Received membership persistence request: ${request.request::class.java} ID: ${request.context.requestId}" }
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
        respFuture.complete(
            MembershipPersistenceResponse(
                buildResponseContext(request.context),
                result
            )
        )
    }

    private fun buildResponseContext(requestContext: MembershipRequestContext): MembershipResponseContext {
        return with(requestContext) {
            MembershipResponseContext(
                requestTimestamp,
                requestId,
                handlerFactories.persistenceHandlerServices.clock.instant(),
                holdingIdentity
            )
        }
    }
}
