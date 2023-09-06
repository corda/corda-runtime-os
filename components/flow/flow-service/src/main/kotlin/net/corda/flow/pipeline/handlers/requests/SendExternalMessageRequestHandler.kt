package net.corda.flow.pipeline.handlers.requests

import net.corda.data.flow.state.waiting.Wakeup as WakeupState
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.external.messaging.services.ExternalMessagingRecordFactory
import net.corda.external.messaging.services.ExternalMessagingRoutingService
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowPlatformException
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.libs.external.messaging.entities.InactiveResponseType
import net.corda.messaging.api.records.Record
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("Unused")
@Component(service = [FlowRequestHandler::class])
class SendExternalMessageRequestHandler @Activate constructor(
    @Reference(service = ExternalMessagingRoutingService::class)
    private val externalMessagingRoutingService: ExternalMessagingRoutingService,
    @Reference(service = ExternalMessagingRecordFactory::class)
    private val externalMessagingRecordFactory: ExternalMessagingRecordFactory,
    @Reference(service = FlowRecordFactory::class)
    private val flowRecordFactory: FlowRecordFactory
) : FlowRequestHandler<FlowIORequest.SendExternalMessage> {

    override val type = FlowIORequest.SendExternalMessage::class.java

    override fun getUpdatedWaitingFor(
        context: FlowEventContext<Any>,
        request: FlowIORequest.SendExternalMessage
    ): WaitingFor {
        return WaitingFor(WakeupState())
    }

    @Suppress("MaxLineLength")
    override fun postProcess(
        context: FlowEventContext<Any>,
        request: FlowIORequest.SendExternalMessage
    ): FlowEventContext<Any> {
        val holdingId = context.checkpoint.holdingIdentity.shortHash.toString()
        val channelName = request.channelName

        val verifiedRoute = externalMessagingRoutingService.getRoute(
            holdingId,
            channelName
        ) ?: throw FlowPlatformException("Failed to send message, no route found for channel='${channelName}'.")

        if (!verifiedRoute.externalReceiveTopicNameExists) {
            throw FlowPlatformException(
                "Failed to send message, topic '${verifiedRoute.route.externalReceiveTopicName}' does not exist for channel '${channelName}'."
            )
        }

        val route = verifiedRoute.route

        if (!route.active) {
            if (route.inactiveResponseType == InactiveResponseType.IGNORE) {
                return getUpdatedContext(context)
            }

            throw FlowPlatformException("Failed to send message, channel='${channelName}' is inactive.")
        }

        return getUpdatedContext(
            context,
            externalMessagingRecordFactory.createSendRecord(
                holdingId,
                route,
                request.messageId,
                request.message
            )
        )
    }

    private fun getUpdatedContext(
        context: FlowEventContext<Any>,
        externalMessage: Record<String, String>? = null
    ): FlowEventContext<Any> {
        val records = if (externalMessage != null) {
            listOf(externalMessage)
        } else {
            emptyList()
        }

        return context.copy(outputRecords = context.outputRecords + records)
    }
}
