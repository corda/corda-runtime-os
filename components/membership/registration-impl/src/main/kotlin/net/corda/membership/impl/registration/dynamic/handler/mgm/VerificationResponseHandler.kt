package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.ApproveRegistration
import net.corda.data.membership.command.registration.mgm.ProcessMemberVerificationResponse
import net.corda.data.membership.db.request.command.RegistrationStatus
import net.corda.data.membership.state.RegistrationState
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandler
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandlerResult
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.Companion.REGISTRATION_COMMAND_TOPIC
import net.corda.virtualnode.toCorda

class VerificationResponseHandler(
    private val membershipPersistenceClient: MembershipPersistenceClient
) : RegistrationHandler<ProcessMemberVerificationResponse> {
    override val commandType = ProcessMemberVerificationResponse::class.java

    override fun invoke(key: String, command: ProcessMemberVerificationResponse): RegistrationHandlerResult {
        val registrationId = command.verificationResponse.registrationId
        val mgm = command.destination
        val member = command.source
        membershipPersistenceClient.setRegistrationRequestStatus(
            mgm.toCorda(),
            registrationId,
            RegistrationStatus.PENDING_AUTO_APPROVAL
        )
        return RegistrationHandlerResult(
            RegistrationState(registrationId, member),
            listOf(
                Record(
                    REGISTRATION_COMMAND_TOPIC,
                    member,
                    RegistrationCommand(ApproveRegistration(mgm, member, registrationId))
                )
            )
        )
    }
}