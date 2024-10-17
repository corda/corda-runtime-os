package net.corda.flow.pipeline.handlers.requests

import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.data.flow.state.waiting.external.ExternalEventResponse
import net.corda.flow.external.events.impl.ExternalEventManager
import net.corda.flow.external.events.impl.factory.ExternalEventFactoryMap
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.utils.toKeyValuePairList
import net.corda.flow.utils.toMap
import net.corda.v5.application.flows.FlowContextPropertyKeys.CPK_FILE_CHECKSUM
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Instant
import net.corda.flow.external.events.ExternalEventContext

@Component(service = [FlowRequestHandler::class])
class ExternalEventRequestHandler @Activate constructor(
    @Reference(service = ExternalEventManager::class)
    private val externalEventManager: ExternalEventManager,
    @Reference(service = ExternalEventFactoryMap::class)
    private val externalEventFactoryMap: ExternalEventFactoryMap
) : FlowRequestHandler<FlowIORequest.ExternalEvent> {

    override val type = FlowIORequest.ExternalEvent::class.java

    override fun getUpdatedWaitingFor(
        context: FlowEventContext<Any>,
        request: FlowIORequest.ExternalEvent
    ): WaitingFor {
        return WaitingFor(ExternalEventResponse(request.requestId))
    }

    override fun postProcess(
        context: FlowEventContext<Any>,
        request: FlowIORequest.ExternalEvent
    ): FlowEventContext<Any> {
        val cpkFileHashes = context.checkpoint.cpkFileHashes
            .toKeyValuePairList(CPK_FILE_CHECKSUM)
            .toMap()

        val eventRecord = externalEventFactoryMap.get(request.factoryClass.name)
            .createExternalEvent(
                context.checkpoint,
                ExternalEventContext(request.requestId, context.checkpoint.flowId, request.contextProperties + cpkFileHashes),
                request.parameters
            )

        context.checkpoint.externalEventState = externalEventManager.processEventToSend(
            context.checkpoint.flowId,
            request.requestId,
            request.factoryClass.name,
            eventRecord,
            Instant.now()
        )
        return context
    }
}
