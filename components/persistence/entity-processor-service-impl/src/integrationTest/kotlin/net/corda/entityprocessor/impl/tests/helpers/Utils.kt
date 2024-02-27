package net.corda.entityprocessor.impl.tests.helpers

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventResponse
import org.junit.jupiter.api.Assertions

fun assertEventResponseWithoutError(flowEvent: FlowEvent) {
    Assertions.assertNull((flowEvent.payload as ExternalEventResponse).error)
}

fun assertEventResponseWithError(flowEvent: FlowEvent) {
    Assertions.assertNotNull((flowEvent.payload as ExternalEventResponse).error)
}