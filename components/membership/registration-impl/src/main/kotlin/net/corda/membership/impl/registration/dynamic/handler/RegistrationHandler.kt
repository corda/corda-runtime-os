package net.corda.membership.impl.registration.dynamic.handler

import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.messaging.api.records.Record
import net.corda.v5.base.exceptions.CordaRuntimeException

interface RegistrationHandler<T> {
    fun invoke(event: Record<String, RegistrationCommand>): RegistrationHandlerResult {
        val command = event.value?.command
        if (commandType.isInstance(command)) {
            @Suppress("unchecked_cast")
            return invoke(event.key, command as T)
        } else {
            throw CordaRuntimeException("Invalid command: $command")
        }
    }

    fun invoke(key: String, command: T): RegistrationHandlerResult

    val commandType: Class<T>
}
