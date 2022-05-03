package net.corda.flow.pipeline.handlers.eventexception

/**
 * The [FlowEventExceptionHandler] is an optional interface to allow the default exception handling to be overridden. for specific [FlowEvent] types
 *
 * @param T The type of event that the [FlowEventExceptionHandler] handles. [T] is equivalent to [FlowEvent.payload] (which returns [Object] and
 * prevents [T] from being more restrictive).
 */
interface FlowEventExceptionHandler<T> : ExceptionHandler {

    /**
     * Gets the event type [Class] that the handler accepts.
     */
    val type: Class<T>
}

