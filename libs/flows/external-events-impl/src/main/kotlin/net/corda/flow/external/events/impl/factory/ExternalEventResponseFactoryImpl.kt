package net.corda.flow.external.events.impl.factory

import java.nio.ByteBuffer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.data.flow.event.external.ExternalEventResponseErrorType
import net.corda.data.flow.event.external.ExternalEventResponseExceptionEnvelope
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [ExternalEventResponseFactory::class])
class ExternalEventResponseFactoryImpl(
    private val serializer: CordaAvroSerializer<Any>,
    private val clock: Clock
) : ExternalEventResponseFactory {

    @Activate
    constructor(
        @Reference(service = CordaAvroSerializationFactory::class)
        cordaAvroSerializationFactory: CordaAvroSerializationFactory
    ) : this(cordaAvroSerializationFactory.createAvroSerializer { }, UTCClock())

    override fun success(flowExternalEventContext: ExternalEventContext, payload: Any, ): Record<String, FlowEvent> {
        val response = ExternalEventResponse.newBuilder()
            .setRequestId(flowExternalEventContext.requestId)
            .setPayload(ByteBuffer.wrap(serializer.serialize(payload)))
            .setExceptionEnvelope(null)
            .setTimestamp(clock.instant())
            .build()
        return flowEvent(flowExternalEventContext, response)
    }

    override fun retriable(
        flowExternalEventContext: ExternalEventContext,
        throwable: Throwable
    ): Record<String, FlowEvent> {
        return retriable(
            flowExternalEventContext,
            ExceptionEnvelope(throwable::class.java.simpleName, throwable.message)
        )
    }

    override fun retriable(
        flowExternalEventContext: ExternalEventContext,
        exceptionEnvelope: ExceptionEnvelope
    ): Record<String, FlowEvent> {
        return error(flowExternalEventContext, exceptionEnvelope, ExternalEventResponseErrorType.RETRY)
    }

    override fun platformError(
        flowExternalEventContext: ExternalEventContext,
        throwable: Throwable
    ): Record<String, FlowEvent> {
        return platformError(
            flowExternalEventContext,
            ExceptionEnvelope(throwable::class.java.simpleName, throwable.message)
        )
    }

    override fun platformError(
        flowExternalEventContext: ExternalEventContext,
        exceptionEnvelope: ExceptionEnvelope
    ): Record<String, FlowEvent> {
        return error(flowExternalEventContext, exceptionEnvelope, ExternalEventResponseErrorType.PLATFORM_ERROR)
    }

    override fun fatalError(
        flowExternalEventContext: ExternalEventContext,
        throwable: Throwable
    ): Record<String, FlowEvent> {
        return fatalError(
            flowExternalEventContext,
            ExceptionEnvelope(throwable::class.java.simpleName, throwable.message)
        )
    }

    override fun fatalError(
        flowExternalEventContext: ExternalEventContext,
        exceptionEnvelope: ExceptionEnvelope
    ): Record<String, FlowEvent> {
        return error(flowExternalEventContext, exceptionEnvelope, ExternalEventResponseErrorType.FATAL_ERROR)
    }

    private fun error(
        flowExternalEventContext: ExternalEventContext,
        exceptionEnvelope: ExceptionEnvelope,
        errorType: ExternalEventResponseErrorType
    ): Record<String, FlowEvent> {
        val response = ExternalEventResponse.newBuilder()
            .setRequestId(flowExternalEventContext.requestId)
            .setPayload(null)
            .setExceptionEnvelope(
                ExternalEventResponseExceptionEnvelope(
                    errorType,
                    exceptionEnvelope
                )
            )
            .setTimestamp(clock.instant())
            .build()
        return flowEvent(flowExternalEventContext, response)
    }

    private fun flowEvent(
        flowExternalEventContext: ExternalEventContext,
        response: ExternalEventResponse
    ): Record<String, FlowEvent> {
        return Record(
            Schemas.Flow.FLOW_EVENT_TOPIC,
            flowExternalEventContext.flowId,
            FlowEvent(flowExternalEventContext.flowId, response)
        )
    }
}