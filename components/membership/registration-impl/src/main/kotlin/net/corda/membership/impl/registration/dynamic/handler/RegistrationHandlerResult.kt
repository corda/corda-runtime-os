package net.corda.membership.impl.registration.dynamic.handler

import net.corda.data.membership.state.RegistrationState
import net.corda.messaging.api.records.Record

data class RegistrationHandlerResult(
    val updatedState: RegistrationState?,
    val outputStates: List<Record<*, *>>
)