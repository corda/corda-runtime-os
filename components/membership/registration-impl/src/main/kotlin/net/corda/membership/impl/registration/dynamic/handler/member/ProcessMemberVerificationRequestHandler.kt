package net.corda.membership.impl.registration.dynamic.handler.member

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.command.registration.member.ProcessMemberVerificationRequest
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.p2p.VerificationResponse
import net.corda.data.membership.state.RegistrationState
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandler
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandlerResult
import net.corda.membership.p2p.helpers.P2pRecordsFactory
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.utilities.time.Clock
import net.corda.virtualnode.toCorda

internal class ProcessMemberVerificationRequestHandler(
    clock: Clock,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val membershipPersistenceClient: MembershipPersistenceClient,
    private val p2pRecordsFactory: P2pRecordsFactory = P2pRecordsFactory(
        cordaAvroSerializationFactory,
        clock,
    )
) : RegistrationHandler<ProcessMemberVerificationRequest> {
    override val commandType = ProcessMemberVerificationRequest::class.java

    override fun invoke(state: RegistrationState?, key: String, command: ProcessMemberVerificationRequest): RegistrationHandlerResult {
        val member = command.destination
        val mgm = command.source
        val registrationId = command.verificationRequest.registrationId

        membershipPersistenceClient.setRegistrationRequestStatus(
            member.toCorda(),
            registrationId,
            RegistrationStatus.PENDING_MEMBER_VERIFICATION,
        )

        return RegistrationHandlerResult(
            null,
            listOf(
                p2pRecordsFactory.createAuthenticatedMessageRecord(
                    member,
                    mgm,
                    VerificationResponse(
                        registrationId,
                        KeyValuePairList(emptyList<KeyValuePair>())
                    )
                )
            )
        )
    }
}
