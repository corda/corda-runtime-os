package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.command.registration.mgm.VerifyMember
import net.corda.data.membership.db.request.command.RegistrationStatus
import net.corda.data.membership.p2p.VerificationRequest
import net.corda.data.membership.state.RegistrationState
import net.corda.membership.impl.registration.dynamic.handler.MissingRegistrationStateException
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandler
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandlerResult
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.messaging.api.records.Record
import net.corda.p2p.app.AppMessage
import net.corda.p2p.app.AuthenticatedMessage
import net.corda.p2p.app.AuthenticatedMessageHeader
import net.corda.schema.Schemas.P2P.Companion.P2P_OUT_TOPIC
import net.corda.utilities.time.Clock
import net.corda.virtualnode.toCorda
import java.nio.ByteBuffer
import java.util.UUID

class VerifyMemberHandler(
    private val clock: Clock,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val membershipPersistenceClient: MembershipPersistenceClient
) : RegistrationHandler<VerifyMember> {

    private companion object {
        const val MEMBERSHIP_P2P_SUBSYSTEM = "membership"
        const val TTL = 1000L
    }

    private val requestSerializer = cordaAvroSerializationFactory.createAvroSerializer<VerificationRequest> {  }

    override val commandType = VerifyMember::class.java

    override fun invoke(state: RegistrationState?, key: String, command: VerifyMember): RegistrationHandlerResult {
        if(state == null) throw MissingRegistrationStateException
        val mgm = state.mgm
        val member = state.registeringMember
        val registrationId = state.registrationId
        val requestTimestamp = clock.instant()
        val authenticatedMessageHeader = AuthenticatedMessageHeader(
            member,
            mgm,
            requestTimestamp.plusMillis(TTL)?.toEpochMilli(),
            UUID.randomUUID().toString(),
            null,
            MEMBERSHIP_P2P_SUBSYSTEM
        )
        val request = VerificationRequest(
            registrationId,
            KeyValuePairList(emptyList<KeyValuePair>())
        )
        val authenticatedMessage = AuthenticatedMessage(
            authenticatedMessageHeader,
            ByteBuffer.wrap(requestSerializer.serialize(request))
        )
        membershipPersistenceClient.setRegistrationRequestStatus(
            mgm.toCorda(),
            registrationId,
            RegistrationStatus.PENDING_MEMBER_VERIFICATION
        )
        return RegistrationHandlerResult(
            RegistrationState(registrationId, member, mgm),
            listOf(
                Record(
                    P2P_OUT_TOPIC,
                    member,
                    AppMessage(authenticatedMessage)
                )
            )
        )
    }
}