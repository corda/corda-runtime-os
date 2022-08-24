package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.membership.command.registration.mgm.DeclineRegistration
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.p2p.SetOwnRegistrationStatus
import net.corda.data.membership.state.RegistrationState
import net.corda.membership.impl.registration.dynamic.handler.MissingRegistrationStateException
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandler
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandlerResult
import net.corda.membership.impl.registration.dynamic.handler.helpers.P2pRecordsFactory
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.utilities.time.Clock
import net.corda.virtualnode.toCorda

class DeclineRegistrationHandler(
    private val membershipPersistenceClient: MembershipPersistenceClient,
    clock: Clock,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val p2pRecordsFactory: P2pRecordsFactory = P2pRecordsFactory(
        cordaAvroSerializationFactory,
        clock,
    ),
) : RegistrationHandler<DeclineRegistration> {
    private companion object {
        const val MINUTES_TO_WAIT_FOR_P2P_MESSAGE = 8L
    }
    override fun invoke(
        state: RegistrationState?,
        key: String,
        command: DeclineRegistration
    ): RegistrationHandlerResult {
        if (state == null) throw MissingRegistrationStateException
        // Update the state of the request and member
        val declinedBy = state.mgm
        val declinedMember = state.registeringMember
        val registrationId = state.registrationId
        membershipPersistenceClient.setMemberAndRegistrationRequestAsDeclined(
            viewOwningIdentity = declinedBy.toCorda(),
            declinedMember = declinedMember.toCorda(),
            registrationRequestId = registrationId,
        )
        val persistDeclineMessage = p2pRecordsFactory.createAuthenticatedMessageRecord(
            source = declinedBy,
            destination = declinedMember,
            // Setting TTL to avoid resending the message in case the decline reason is that the
            // P2P channel could not be established.
            minutesToWait = MINUTES_TO_WAIT_FOR_P2P_MESSAGE,
            content = SetOwnRegistrationStatus(
                registrationId,
                RegistrationStatus.DECLINED
            )
        )
        return RegistrationHandlerResult(
            state,
            listOf(persistDeclineMessage)
        )
    }

    override val commandType: Class<DeclineRegistration>
        get() = DeclineRegistration::class.java
}
