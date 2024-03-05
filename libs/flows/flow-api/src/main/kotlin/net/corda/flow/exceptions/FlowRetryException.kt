package net.corda.flow.exceptions

/**
 * Signals to the flow engine that the flow should be retried from the previous checkpoint.
 *
 * This will prevent any update to the current checkpoint and signal to the event mediator to re-poll the batch of events
 * that lead to this problem.
 */
class FlowRetryException(message: String) : Exception(message)