package net.corda.flow.pipeline.exceptions

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * The [FlowMarkedForKillException] is thrown when a flow processing is to be stopped and the flow is to be killed.
 */
class FlowMarkedForKillException(
    message: String,
    val details: Map<String, String>? = null,
) : CordaRuntimeException(message)

