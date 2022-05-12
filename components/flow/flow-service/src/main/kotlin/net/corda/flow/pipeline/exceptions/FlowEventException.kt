package net.corda.flow.pipeline.exceptions

import net.corda.flow.pipeline.FlowEventContext

/**
 * The [FlowEventException] is thrown when event processing needs to be aborted, and the state and output messages
 * need to be published.
 *
 * Example:
 * A Session Event is received and updates the session to an error state, in this case we want to abort further
 * processing but still output the updated state.
 */
class FlowEventException(message: String, flowEventContext: FlowEventContext<Any>, cause: Throwable? = null) :
    FlowProcessingException(message, flowEventContext, cause)