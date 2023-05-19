package net.corda.membership.impl.registration.dynamic.handler

import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.state.RegistrationState
import net.corda.membership.lib.metrics.TimerMetricTypes
import net.corda.membership.lib.metrics.getTimerMetric
import net.corda.messaging.api.records.Record
import net.corda.v5.base.exceptions.CordaRuntimeException

interface RegistrationHandler<T> {
    fun invoke(state: RegistrationState?, event: Record<String, RegistrationCommand>): RegistrationHandlerResult {
        event.value?.command?.let { command ->
            if (commandType.isInstance(command)) {
                @Suppress("unchecked_cast")
                return getTimerMetric(
                    TimerMetricTypes.REGISTRATION,
                    commandType.simpleName
                ).recordCallable {
                    invoke(state, event.key, command as T)
                }!!
            } else {
                throw CordaRuntimeException("Invalid command: $command")
            }
        } ?: throw CordaRuntimeException("Command cannot be null.")
    }

    fun invoke(state: RegistrationState?, key: String, command: T): RegistrationHandlerResult

    val commandType: Class<T>
}
