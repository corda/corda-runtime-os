package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.command.registration.mgm.VerifyMember
import net.corda.data.membership.p2p.SetOwnRegistrationStatus
import net.corda.data.membership.p2p.VerificationRequest
import net.corda.data.membership.rpc.response.RegistrationStatus
import net.corda.data.membership.state.RegistrationState
import net.corda.membership.impl.registration.dynamic.handler.MissingRegistrationStateException
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandler
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandlerResult
import net.corda.membership.impl.registration.dynamic.handler.helpers.P2pRecordsFactory
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.utilities.time.Clock
import net.corda.virtualnode.toCorda

class VerifyMemberHandler(
    clock: Clock,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val membershipPersistenceClient: MembershipPersistenceClient,
    private val p2pRecordsFactory: P2pRecordsFactory = P2pRecordsFactory(
        cordaAvroSerializationFactory,
        clock,
    )
) : RegistrationHandler<VerifyMember> {
    override val commandType = VerifyMember::class.java

    override fun invoke(state: RegistrationState?, key: String, command: VerifyMember): RegistrationHandlerResult {
        if(state == null) throw MissingRegistrationStateException
        val mgm = state.mgm
        val member = state.registeringMember
        val registrationId = state.registrationId
        membershipPersistenceClient.setRegistrationRequestStatus(
            mgm.toCorda(),
            registrationId,
            RegistrationStatus.PENDING_MEMBER_VERIFICATION
        )
        return RegistrationHandlerResult(
            RegistrationState(registrationId, member, mgm),
            listOf(
                p2pRecordsFactory.createAuthenticatedMessageRecord(
                    source = mgm,
                    destination = member,
                    content = SetOwnRegistrationStatus(
                        registrationId,
                        RegistrationStatus.PENDING_MEMBER_VERIFICATION
                    )
                ),
                p2pRecordsFactory.createAuthenticatedMessageRecord(
                    mgm,
                    member,
                    VerificationRequest(
                        registrationId,
                        KeyValuePairList(emptyList<KeyValuePair>())
                    )
                )
            )
        )
    }
}