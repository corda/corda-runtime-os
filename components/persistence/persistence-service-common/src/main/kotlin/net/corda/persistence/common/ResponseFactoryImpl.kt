package net.corda.persistence.common

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.flow.event.external.ExternalEventResponseErrorType
import net.corda.flow.external.events.responses.exceptions.CpkNotAvailableException
import net.corda.flow.external.events.responses.exceptions.VirtualNodeException
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.messaging.api.exception.CordaHTTPServerTransientException
import net.corda.messaging.api.records.Record
import net.corda.orm.PersistenceExceptionCategorizer
import net.corda.orm.PersistenceExceptionType
import net.corda.persistence.common.exceptions.KafkaMessageSizeException
import net.corda.persistence.common.exceptions.MissingAccountContextPropertyException
import net.corda.persistence.common.exceptions.NullParameterException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.io.NotSerializableException
import java.sql.SQLException
import javax.persistence.PersistenceException

@Component(service = [ResponseFactory::class])
class ResponseFactoryImpl @Activate internal constructor(
    @Reference(service = ExternalEventResponseFactory::class)
    private val externalEventResponseFactory: ExternalEventResponseFactory,
    @Reference(service = PersistenceExceptionCategorizer::class)
    private val persistenceExceptionCategorizer: PersistenceExceptionCategorizer
) : ResponseFactory {

    private companion object{
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun successResponse(
        flowExternalEventContext: ExternalEventContext,
        payload: Any
    ): Record<String, FlowEvent> {
        return externalEventResponseFactory.success(flowExternalEventContext, payload)
    }

    override fun errorResponse(externalEventContext : ExternalEventContext, exception: Exception) = when (exception) {
        is CpkNotAvailableException, is VirtualNodeException -> {
            throw CordaHTTPServerTransientException(externalEventContext.requestId, exception)
        }
        is NotSerializableException, is NullParameterException -> {
            platformErrorResponse(externalEventContext, exception)
        }
        is KafkaMessageSizeException, is MissingAccountContextPropertyException -> {
            fatalErrorResponse(externalEventContext, exception)
        }
        is PersistenceException, is SQLException -> {
            when (persistenceExceptionCategorizer.categorize(exception)) {
                PersistenceExceptionType.FATAL -> fatalErrorResponse(externalEventContext, exception)
                PersistenceExceptionType.DATA_RELATED,
                PersistenceExceptionType.UNCATEGORIZED -> platformErrorResponse(externalEventContext, exception)
                PersistenceExceptionType.TRANSIENT -> throw CordaHTTPServerTransientException(externalEventContext.requestId, exception)
            }
        }
        else -> {
            platformErrorResponse(externalEventContext, exception)
        }
    }

    override fun platformErrorResponse(
        flowExternalEventContext: ExternalEventContext,
        e: Exception
    ): Record<String, FlowEvent> {
        log.warn(errorLogMessage(flowExternalEventContext, ExternalEventResponseErrorType.PLATFORM), e)
        return externalEventResponseFactory.platformError(flowExternalEventContext, e)
    }

    override fun fatalErrorResponse(
        flowExternalEventContext: ExternalEventContext,
        e: Exception
    ): Record<String, FlowEvent> {
        log.error(errorLogMessage(flowExternalEventContext, ExternalEventResponseErrorType.FATAL), e)
        return externalEventResponseFactory.fatalError(flowExternalEventContext, e)
    }

    private fun errorLogMessage(
        flowExternalEventContext: ExternalEventContext,
        errorType: ExternalEventResponseErrorType
    ): String {
        return "Exception occurred (type=$errorType) for flow-worker request ${flowExternalEventContext.requestId}"
    }
}