package net.corda.membership.impl.registration.dynamic.handler

import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.state.RegistrationState
import net.corda.messaging.api.records.Record
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.slf4j.LoggerFactory

interface RegistrationHandler<T> {
    private companion object {
        val logger = LoggerFactory.getLogger("QQQ - RegistrationHandler")
    }
    fun invoke(state: RegistrationState?, event: Record<String, RegistrationCommand>): RegistrationHandlerResult {
        val command = event.value?.command
        val start = System.currentTimeMillis()
        if (commandType.isInstance(command)) {
            @Suppress("unchecked_cast")
            return invoke(state, event.key, command as T).also {
                logger.info("QQQ command: ${(command as? Any)?.javaClass?.simpleName} took ${System.currentTimeMillis() - start}")
            }
        } else {
            throw CordaRuntimeException("Invalid command: $command")
        }
    }

    fun invoke(state: RegistrationState?, key: String, command: T): RegistrationHandlerResult

    val commandType: Class<T>
}
