package net.corda.flow.pipeline.handlers.waiting.db


import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.state.db.Query
import net.corda.data.flow.state.waiting.EntityResponse
import net.corda.data.persistence.DeleteEntity
import net.corda.data.persistence.EntityRequest
import net.corda.data.persistence.EntityResponseFailure
import net.corda.data.persistence.EntityResponseSuccess
import net.corda.data.persistence.Error
import net.corda.data.persistence.FindEntity
import net.corda.data.persistence.MergeEntity
import net.corda.data.persistence.PersistEntity
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.handlers.waiting.FlowWaitingForHandler
import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.FlowConfig.DB_MAX_RETRIES
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.persistence.CordaPersistenceException
import org.osgi.service.component.annotations.Component

@Component(service = [FlowWaitingForHandler::class])
class EntityResponseWaitingForHandler : FlowWaitingForHandler<EntityResponse> {

    private companion object {
        val log = contextLogger()
    }

    override val type = EntityResponse::class.java

    override fun runOrContinue(context: FlowEventContext<*>, waitingFor: EntityResponse): FlowContinuation {
        val checkpoint = context.checkpoint
        val config = context.config
        val query = checkpoint.query
        if (query?.request?.request != null) {
            log.debug { "Check for response for request type ${query.request.request::class} and id ${query.requestId}" }
        }
        val response = query?.response
        return if (response != null) {
            val responseType = response.responseType
            log.debug { "Response received of type ${response.responseType::class} for request id ${response.requestId}" }
            if (responseType is EntityResponseSuccess) {
                val entityResponse = getPayloadFromResponse(query.request, responseType)
                context.checkpoint.query = null
                entityResponse
            } else {
                return handleErrorResponse(response.responseType as EntityResponseFailure, config, query)
            }
        } else {
            log.debug { "No response received yet for request id ${query?.requestId}" }
            FlowContinuation.Continue
        }
    }

    private fun handleErrorResponse(
        errorResponse: EntityResponseFailure,
        config: SmartConfig,
        query: Query,
    ): FlowContinuation {
        val errorType = errorResponse.errorType
        val errorException = errorResponse.exception
        val retries = query.retries
        val errorMessage = "$errorType exception returned from the db worker for query"
        val retryErrorMessage = "$errorMessage. Retrying exception after delay. Current retry count $retries. Exception: $errorException"
        val maxRetryErrorMessage = "$errorMessage. Exceeded max retries. Exception: $errorException"
        return when (errorType) {
            Error.FATAL -> {
                log.error("$errorMessage. Exception: $errorException")
                FlowContinuation.Error(CordaPersistenceException(errorException.errorMessage))
            }
            Error.NOT_READY -> {
                log.warn(retryErrorMessage)
                query.retries = retries.inc()
                FlowContinuation.Continue
            }
            Error.VIRTUAL_NODE -> {
                handleRetriableError(config, errorException, maxRetryErrorMessage, retryErrorMessage, query)
            }
            Error.DATABASE -> {
                handleRetriableError(config, errorException, maxRetryErrorMessage, retryErrorMessage, query)
            }
            else -> {
                log.error("Unexpected error type returned from the DB worker: $errorType")
                FlowContinuation.Error(CordaPersistenceException(errorException.errorMessage))
            }
        }
    }

    private fun handleRetriableError(
        config: SmartConfig,
        errorException: ExceptionEnvelope,
        maxRetryErrorMessage: String,
        retryErrorMessage: String,
        query: Query
    ) : FlowContinuation {
        val retries = query.retries
        return if (retries >= config.getLong(DB_MAX_RETRIES)) {
            log.error(maxRetryErrorMessage)
            FlowContinuation.Error(CordaPersistenceException(errorException.errorMessage))
        } else {
            log.warn(retryErrorMessage)
            query.retries = retries.inc()
            FlowContinuation.Continue
        }
    }

    private fun getPayloadFromResponse(request: EntityRequest, response: EntityResponseSuccess): FlowContinuation {
        return when (request.request) {
            is FindEntity,
            is MergeEntity -> {
                FlowContinuation.Run(response.result)
            }
            is DeleteEntity,
            is PersistEntity -> {
                FlowContinuation.Run(Unit)
            }
            else -> {
                FlowContinuation.Continue
            }
        }
    }
}
