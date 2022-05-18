package net.corda.flow.pipeline.sessions

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * [FlowSessionMissingException] is thrown when a session is missing from a flow.
 */
class FlowSessionMissingException(override val message: String) : CordaRuntimeException(message)