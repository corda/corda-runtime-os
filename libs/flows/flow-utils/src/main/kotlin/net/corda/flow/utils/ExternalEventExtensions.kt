package net.corda.flow.utils

import net.corda.flow.external.events.ExternalEventContext
import net.corda.data.flow.event.external.ExternalEventContext as AvroExternalEventContext

fun ExternalEventContext.toAvro() = AvroExternalEventContext(
    this.flowId,
    this.requestId,
    keyValuePairListOf(this.parameters)
)
