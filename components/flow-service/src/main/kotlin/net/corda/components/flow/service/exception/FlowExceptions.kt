package net.corda.components.flow.service.exception

import net.corda.messaging.api.exception.CordaMessageAPIDLQException
import net.corda.messaging.api.exception.CordaMessageAPISkipRecordException

/**
 * Exceptions related to Flow message processing that should result in a flow being sent to the flow hospital/DLQ
 */
class FlowHospitalException(message: String?) : CordaMessageAPIDLQException(message)

/**
 * Exceptions related to Flow message processing that should result in an event being skipped.
 */
class FlowMessageSkipException(message: String?) : CordaMessageAPISkipRecordException(message)