package net.corda.flow.service.exception

import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException

//TODO - CORE-2953 - update these to message api exceptions that meet these semantics whenever they are made.
/**
 * Exceptions related to Flow message processing that should result in a flow being sent to the flow hospital/DLQ
 */
class FlowHospitalException(message: String?) : CordaMessageAPIIntermittentException(message)

/**
 * Exceptions related to Flow message processing that should result in an event being skipped.
 */
class FlowMessageSkipException(message: String?) : CordaMessageAPIIntermittentException(message)