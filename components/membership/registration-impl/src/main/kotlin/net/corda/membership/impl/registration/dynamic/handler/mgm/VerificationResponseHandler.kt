package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.ProcessMemberVerificationResponse
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandler
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandlerResult
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger

class VerificationResponseHandler(
    cordaAvroSerializationFactory: CordaAvroSerializationFactory
) : RegistrationHandler {
    private companion object {
        val logger = contextLogger()
    }

    override fun invoke(command: Record<String, RegistrationCommand>): RegistrationHandlerResult {
        logger.info("Handling request.")
        val inputCommand = command.value?.command as? ProcessMemberVerificationResponse
        require(inputCommand != null) {
            "Incorrect handler used for command of type ${command.value!!.command::class.java}"
        }
        TODO("Not yet implemented")
    }
}