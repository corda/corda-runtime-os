package net.corda.ledger.persistence

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventResponse
import org.assertj.core.api.Assertions
import org.slf4j.Logger

fun assertSuccessResponse(flowEvent: FlowEvent, logger: Logger): ExternalEventResponse {
    val response = flowEvent.payload as ExternalEventResponse
    if (response.error != null) {
        logger.error("Incorrect error response: {}", response.error)
    }
    Assertions.assertThat(response.error).isNull()
    return response
}