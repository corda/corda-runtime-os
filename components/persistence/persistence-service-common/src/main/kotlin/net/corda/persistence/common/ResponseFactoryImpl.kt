package net.corda.persistence.common

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.flow.event.external.ExternalEventResponseErrorType
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.messaging.api.records.Record
import net.corda.persistence.common.exceptions.KafkaMessageSizeException
import net.corda.persistence.common.exceptions.NotReadyException
import net.corda.persistence.common.exceptions.NullParameterException
import net.corda.persistence.common.exceptions.VirtualNodeException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.io.NotSerializableException

@Component(service = [ResponseFactory::class])
class ResponseFactoryImpl @Activate constructor(
    @Reference(service = ExternalEventResponseFactory::class)
    private val externalEventResponseFactory: ExternalEventResponseFactory
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
        is NotReadyException, is VirtualNodeException ->
            transientErrorResponse(externalEventContext, exception)
        is NotSerializableException ->
            platformErrorResponse(externalEventContext, exception)
        is KafkaMessageSizeException, is NullParameterException ->
            fatalErrorResponse(externalEventContext, exception)
        else -> transientErrorResponse(externalEventContext, exception)
    }

    override fun transientErrorResponse(
        flowExternalEventContext: ExternalEventContext,
        e: Exception
    ): Record<String, FlowEvent> {
        log.warn(errorLogMessage(flowExternalEventContext, ExternalEventResponseErrorType.TRANSIENT), e)
        return externalEventResponseFactory.transientError(flowExternalEventContext, e)
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