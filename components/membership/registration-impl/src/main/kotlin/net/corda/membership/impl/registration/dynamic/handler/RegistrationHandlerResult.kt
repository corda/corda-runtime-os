package net.corda.membership.impl.registration.dynamic.handler

import net.corda.data.membership.state.RegistrationState
import net.corda.messaging.api.records.Record
import net.corda.v5.base.exceptions.CordaRuntimeException

data class RegistrationHandlerResult(
    val updatedState: RegistrationState?,
    val outputStates: List<Record<*, *>>
)

internal object MissingRegistrationStateException : CordaRuntimeException("RegistrationState is missing.")