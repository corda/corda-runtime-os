package net.corda.entityprocessor.impl.tests.helpers

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.messaging.api.records.Record
import org.junit.jupiter.api.Assertions

sealed class QuerySetup {
    data class NamedQuery(val params: Map<String, String>, val query: String = "Dog.summon") : QuerySetup()
    data class All(val className: String) : QuerySetup()
}

fun assertEventResponseWithoutError(record: Record<*, *>) {
    Assertions.assertNull(((record.value as FlowEvent).payload as ExternalEventResponse).error)
}

fun assertEventResponseWithError(record: Record<*, *>) {
    Assertions.assertNotNull(((record.value as FlowEvent).payload as ExternalEventResponse).error)
}