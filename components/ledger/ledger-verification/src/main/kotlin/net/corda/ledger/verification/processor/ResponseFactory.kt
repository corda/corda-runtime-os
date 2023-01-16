package net.corda.ledger.verification.processor

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.messaging.api.records.Record

interface ResponseFactory {
    fun successResponse(
        flowExternalEventContext: ExternalEventContext,
        payload: Any
    ): Record<String, FlowEvent>

    fun errorResponse(externalEventContext : ExternalEventContext, exception: Exception): Record<String, FlowEvent>
    fun transientErrorResponse(
        flowExternalEventContext: ExternalEventContext,
        e: Exception
    ): Record<String, FlowEvent>

    fun platformErrorResponse(
        flowExternalEventContext: ExternalEventContext,
        e: Exception
    ): Record<String, FlowEvent>

    fun fatalErrorResponse(
        flowExternalEventContext: ExternalEventContext,
        e: Exception
    ): Record<String, FlowEvent>
}