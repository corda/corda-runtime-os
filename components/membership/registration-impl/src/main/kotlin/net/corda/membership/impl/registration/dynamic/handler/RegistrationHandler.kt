package net.corda.membership.impl.registration.dynamic.handler

import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.state.RegistrationState
import net.corda.membership.lib.metrics.TimerMetricTypes
import net.corda.membership.lib.metrics.getTimerMetric
import net.corda.messaging.api.records.Record
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.toCorda

interface RegistrationHandler<T> {
    fun invoke(state: RegistrationState?, event: Record<String, RegistrationCommand>): RegistrationHandlerResult {
        event.value?.command?.let { command ->
            if (commandType.isInstance(command)) {
                @Suppress("unchecked_cast")
                return recordTimerMetric(state, event.key, command as T) { s, k, c ->
                    invoke(s, k, c)
                }
            } else {
                throw CordaRuntimeException("Invalid command: $command")
            }
        } ?: throw CordaRuntimeException("Command cannot be null.")
    }

    fun recordTimerMetric(
        state: RegistrationState?,
        key: String,
        command: T,
        func: (RegistrationState?, String, T) -> RegistrationHandlerResult
    ): RegistrationHandlerResult {
        return getTimerMetric(
            TimerMetricTypes.REGISTRATION,
            getOwnerHoldingId(state, command)?.toCorda()?.shortHash?.value,
            commandType.simpleName
        ).recordCallable {
            func(state, key, command)
        }!!
    }

    /**
     * Parses out the holding identity short hash of the target virtual node for the registration handler from either
     * the state or command.
     */
    fun getOwnerHoldingId(state: RegistrationState?, command: T): HoldingIdentity?

    fun invoke(state: RegistrationState?, key: String, command: T): RegistrationHandlerResult

    val commandType: Class<T>
}
