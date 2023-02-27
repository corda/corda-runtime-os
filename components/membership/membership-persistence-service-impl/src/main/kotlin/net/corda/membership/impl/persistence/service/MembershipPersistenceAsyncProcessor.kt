package net.corda.membership.impl.persistence.service

import net.corda.data.membership.db.request.async.MembershipPersistenceAsyncRequest
import net.corda.data.membership.db.request.async.MembershipPersistenceAsyncRequestState
import net.corda.membership.impl.persistence.service.handler.HandlerFactories
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.persistence.OptimisticLockException
import javax.persistence.PessimisticLockException

internal class MembershipPersistenceAsyncProcessor(
    private val handlers: HandlerFactories,
) : StateAndEventProcessor<String, MembershipPersistenceAsyncRequestState, MembershipPersistenceAsyncRequest> {
    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val MAX_RETRIES = 10
    }
    override fun onNext(
        state: MembershipPersistenceAsyncRequestState?,
        event: Record<String, MembershipPersistenceAsyncRequest>,
    ): StateAndEventProcessor.Response<MembershipPersistenceAsyncRequestState> {
        val numberOfRetriesSoFar = state?.numberOfRetriesSoFar ?: 0
        val request = event.value
        if (request == null) {
            logger.warn("Empty request for ${event.key}")
            return StateAndEventProcessor.Response(
                updatedState = null,
                responseEvents = emptyList(),
                markForDLQ = true,
            )
        }
        return try {
            handlers.getHandler(request.javaClass).invoke(request.context, request.request)
            StateAndEventProcessor.Response(
                updatedState = null,
                responseEvents = emptyList(),
                markForDLQ = false,
            )
        } catch (e: PessimisticLockException) {
            retry(
                event.key,
                e,
                numberOfRetriesSoFar,
                request,
            )
        } catch (e: OptimisticLockException) {
            retry(
                event.key,
                e,
                numberOfRetriesSoFar,
                request,
            )
        } catch (e: Exception) {
            error(event.key, e)
        }
    }

    private fun retry(
        key: String,
        e: Exception,
        numberOfRetriesSoFar: Int,
        request: MembershipPersistenceAsyncRequest
    ): StateAndEventProcessor.Response<MembershipPersistenceAsyncRequestState> {
        return if (numberOfRetriesSoFar < MAX_RETRIES) {
            logger.warn("Got error while trying to execute $key. Will retry again.", e)
            StateAndEventProcessor.Response(
                updatedState = MembershipPersistenceAsyncRequestState(
                    request,
                    numberOfRetriesSoFar + 1,
                    handlers.persistenceHandlerServices.clock.instant(),
                ),
                responseEvents = emptyList(),
                markForDLQ = false,
            )
        } else {
            error(key, e)
        }
    }
    private fun error(
        key: String,
        e: Exception,
    ): StateAndEventProcessor.Response<MembershipPersistenceAsyncRequestState> {
        logger.warn("Got error while trying to execute $key.", e)
        return StateAndEventProcessor.Response(
            updatedState = null,
            responseEvents = emptyList(),
            markForDLQ = true,
        )
    }

    override val keyClass = String::class.java
    override val stateValueClass = MembershipPersistenceAsyncRequestState::class.java
    override val eventValueClass = MembershipPersistenceAsyncRequest::class.java
}
