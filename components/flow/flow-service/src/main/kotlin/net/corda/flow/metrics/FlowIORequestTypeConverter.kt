package net.corda.flow.metrics

import net.corda.flow.fiber.FlowIORequest

interface FlowIORequestTypeConverter {
    fun convertToActionName(ioRequest: FlowIORequest<Any?>):String
}