package net.corda.persistence.common

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.persistence.EntityResponse
import net.corda.messaging.api.records.Record

interface ResponseFactory {
    fun successResponse(
        flowExternalEventContext: ExternalEventContext,
        entityResponse: EntityResponse
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