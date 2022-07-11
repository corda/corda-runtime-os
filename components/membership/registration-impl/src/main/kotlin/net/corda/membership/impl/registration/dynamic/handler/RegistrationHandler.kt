package net.corda.membership.impl.registration.dynamic.handler

import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.messaging.api.records.Record

interface RegistrationHandler {
    fun invoke(command: Record<String, RegistrationCommand>): RegistrationHandlerResult
}