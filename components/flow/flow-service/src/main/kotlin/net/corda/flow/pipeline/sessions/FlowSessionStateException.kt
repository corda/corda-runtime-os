package net.corda.flow.pipeline.sessions

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * [FlowSessionStateException] is thrown when a session is missing or not in valid state.
 */
class FlowSessionStateException(override val message: String) : CordaRuntimeException(message)