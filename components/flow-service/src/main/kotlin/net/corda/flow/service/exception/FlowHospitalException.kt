package net.corda.flow.service.exception

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Exceptions related to Flow message processing that should result in a flow being sent to the flow hospital/DLQ
 */
class FlowHospitalException(message: String?) : CordaRuntimeException(message)
