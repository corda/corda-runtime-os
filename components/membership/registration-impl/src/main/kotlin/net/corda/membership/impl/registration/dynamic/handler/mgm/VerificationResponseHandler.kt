package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.ProcessMemberVerificationResponse
import net.corda.data.membership.command.registration.mgm.VerifyMember
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandler
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandlerResult
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger

class VerificationResponseHandler(
) : RegistrationHandler<ProcessMemberVerificationResponse> {
    override val commandType = ProcessMemberVerificationResponse::class.java

    override fun invoke(key: String, command: ProcessMemberVerificationResponse): RegistrationHandlerResult {
        TODO("Not yet implemented")
    }
}