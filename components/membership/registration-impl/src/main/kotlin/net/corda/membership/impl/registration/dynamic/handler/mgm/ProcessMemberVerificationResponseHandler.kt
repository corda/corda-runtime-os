package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.ApproveRegistration
import net.corda.data.membership.command.registration.mgm.ProcessMemberVerificationResponse
import net.corda.data.membership.p2p.SetOwnRegistrationStatus
import net.corda.data.membership.state.RegistrationState
import net.corda.membership.impl.registration.dynamic.handler.MissingRegistrationStateException
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandler
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandlerResult
import net.corda.membership.impl.registration.dynamic.handler.helpers.P2pRecordsFactory
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.Companion.REGISTRATION_COMMAND_TOPIC
import net.corda.utilities.time.Clock
import net.corda.virtualnode.toCorda

class ProcessMemberVerificationResponseHandler(
    private val membershipPersistenceClient: MembershipPersistenceClient,
    clock: Clock,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val p2pRecordsFactory: P2pRecordsFactory = P2pRecordsFactory(
        cordaAvroSerializationFactory,
        clock,
    ),
) : RegistrationHandler<ProcessMemberVerificationResponse> {
    override val commandType = ProcessMemberVerificationResponse::class.java

    override fun invoke(state: RegistrationState?, key: String, command: ProcessMemberVerificationResponse): RegistrationHandlerResult {
        if(state == null) throw MissingRegistrationStateException
        val registrationId = state.registrationId
        val mgm = state.mgm
        val member = state.registeringMember
        membershipPersistenceClient.setRegistrationRequestStatus(
            mgm.toCorda(),
            registrationId,
            RegistrationStatus.PENDING_AUTO_APPROVAL
        )
        val persistStatusMessage = p2pRecordsFactory.createAuthenticatedMessageRecord(
            source = mgm,
            destination = member,
            content = SetOwnRegistrationStatus(
                registrationId,
                RegistrationStatus.PENDING_AUTO_APPROVAL
            )
        )
        return RegistrationHandlerResult(
            RegistrationState(registrationId, member, mgm),
            listOf(
                persistStatusMessage,
                Record(
                    REGISTRATION_COMMAND_TOPIC,
                    "$registrationId-${mgm.toCorda().shortHash}",
                    RegistrationCommand(ApproveRegistration())
                )
            )
        )
    }
}