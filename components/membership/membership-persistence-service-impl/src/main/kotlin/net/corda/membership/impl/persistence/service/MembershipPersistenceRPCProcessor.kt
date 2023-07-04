package net.corda.membership.impl.persistence.service

import net.corda.data.membership.db.request.MembershipPersistenceRequest
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.membership.impl.persistence.service.handler.HandlerFactories
import net.corda.data.membership.db.response.MembershipPersistenceResponse
import net.corda.data.membership.db.response.MembershipResponseContext
import net.corda.data.membership.db.response.query.ErrorKind
import net.corda.data.membership.db.response.query.PersistenceFailedResponse
import net.corda.membership.lib.Ticker
import net.corda.membership.lib.exceptions.InvalidEntityUpdateException
import net.corda.messaging.api.processor.RPCResponderProcessor
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
        logger.info(
            "Received membership persistence request: ${request.request::class.java} " +
                "ID: ${request.context.requestId}"
        )
        Ticker.tick("onNext 1")
        val result = try {
            val result = handlerFactories.handle(
                request,
            )
            Ticker.tick("onNext 2")
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
        Ticker.tick("onNext 3")
        respFuture.complete(
            MembershipPersistenceResponse(
                buildResponseContext(request.context),
                result
            )
        )
        Ticker.tick("onNext 4")
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
