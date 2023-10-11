package net.corda.membership.impl.persistence.service

import net.corda.data.membership.db.request.async.MembershipPersistenceAsyncRequest
import net.corda.data.membership.db.request.async.MembershipPersistenceAsyncRequestState
import net.corda.membership.impl.persistence.service.handler.HandlerFactories
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.processor.StateAndEventProcessor.State
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
        state: State<MembershipPersistenceAsyncRequestState>?,
        event: Record<String, MembershipPersistenceAsyncRequest>,
    ): StateAndEventProcessor.Response<MembershipPersistenceAsyncRequestState> {
        val request = event.value
        if (request == null) {
            logger.warn("Empty request for ${event.key}")
            return StateAndEventProcessor.Response(
                updatedState = null,
                responseEvents = emptyList(),
                markForDLQ = true,
            )
        }
        logger.info(
            "Received membership async persistence request: " +
                "${request.request.request::class.java} ID: ${request.request.context.requestId}"
        )
        return try {
            handlers.handle(request.request)
            StateAndEventProcessor.Response(
                updatedState = null,
                responseEvents = emptyList(),
                markForDLQ = false,
            )
        } catch (e: PessimisticLockException) {
            retry(
                event.key,
                e,
                state,
                request,
            )
        } catch (e: OptimisticLockException) {
            retry(
                event.key,
                e,
                state,
                request,
            )
        } catch (e: RecoverableException) {
            retry(
                event.key,
                e,
                state,
                request,
            )
        } catch (e: Exception) {
            error(event.key, e)
        }
    }

    private fun retry(
        key: String,
        e: Exception,
        state: State<MembershipPersistenceAsyncRequestState>?,
        request: MembershipPersistenceAsyncRequest
    ): StateAndEventProcessor.Response<MembershipPersistenceAsyncRequestState> {
        val numberOfRetriesSoFar = state?.value?.numberOfRetriesSoFar ?: 0
        return if (numberOfRetriesSoFar < MAX_RETRIES) {
            logger.warn("Got error while trying to execute $key. Will retry again.", e)
            StateAndEventProcessor.Response(
                updatedState = State(
                    MembershipPersistenceAsyncRequestState(
                        request,
                        numberOfRetriesSoFar + 1,
                        handlers.persistenceHandlerServices.clock.instant(),
                    ),
                    metadata = state?.metadata,
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
