package net.corda.flow.metrics.impl

import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.metrics.FlowIORequestTypeConverter
import org.osgi.service.component.annotations.Component

@Suppress("Unused")
@Component(service = [FlowIORequestTypeConverter::class])
class FlowIORequestTypeConverterImpl : FlowIORequestTypeConverter {

    override fun convertToActionName(ioRequest: FlowIORequest<Any?>): String {
        return when (ioRequest) {
            is FlowIORequest.Send -> "Peer Send"
            is FlowIORequest.Receive -> "Await Peer Data"
            is FlowIORequest.SendAndReceive -> "Peer Send and Await Response"
            is FlowIORequest.ExternalEvent -> {
                "Call - ${simplifyExternalEventFactoryName(ioRequest.factoryClass)}"
            }

            is FlowIORequest.InitialCheckpoint -> "Initial Checkpoint"
            is FlowIORequest.CounterPartyFlowInfo -> "Get Counterparty Info"
            is FlowIORequest.SubFlowFailed -> "Sub Flow Failed"
            is FlowIORequest.SubFlowFinished -> "Sub Flow Finished"
            is FlowIORequest.CloseSessions -> "End Peer Session"
            else -> ioRequest.javaClass.name
        }
    }

    private fun simplifyExternalEventFactoryName(clazz: Class<out ExternalEventFactory<out Any, *, *>>): String {
        return clazz.name.replace("EventFactory", "").replace("Factory", "")
    }
}
