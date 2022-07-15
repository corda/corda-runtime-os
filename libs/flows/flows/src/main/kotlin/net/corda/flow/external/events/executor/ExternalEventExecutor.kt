package net.corda.flow.external.events.executor

import net.corda.flow.external.events.handler.ExternalEventHandler
import net.corda.v5.base.annotations.Suspendable

interface ExternalEventExecutor {

    @Suspendable
    fun <PARAMETERS : Any, RESPONSE, RESUME, T : ExternalEventHandler<PARAMETERS, RESPONSE, RESUME>> execute(
        requestId: String,
        handlerClass: Class<T>,
        parameters: PARAMETERS
    ): RESUME

    @Suspendable
    fun <PARAMETERS : Any, RESPONSE, RESUME, T : ExternalEventHandler<PARAMETERS, RESPONSE, RESUME>> execute(
        handlerClass: Class<T>,
        parameters: PARAMETERS
    ): RESUME
}