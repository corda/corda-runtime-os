package net.corda.membership.impl.registration.dynamic.handler.member

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.command.registration.member.ProcessMemberVerificationRequest
import net.corda.data.membership.p2p.VerificationResponse
import net.corda.data.membership.state.RegistrationState
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandler
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandlerResult
import net.corda.messaging.api.records.Record
import net.corda.p2p.app.AppMessage
import net.corda.p2p.app.AuthenticatedMessage
import net.corda.p2p.app.AuthenticatedMessageHeader
import net.corda.schema.Schemas.P2P.Companion.P2P_OUT_TOPIC
import net.corda.utilities.time.Clock
import java.nio.ByteBuffer
import java.util.UUID

internal class VerificationRequestHandler(
    private val clock: Clock,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory
) : RegistrationHandler<ProcessMemberVerificationRequest> {
    private companion object {
        const val MEMBERSHIP_P2P_SUBSYSTEM = "membership"
        const val TTL = 300000L
    }

    private val responseSerializer = cordaAvroSerializationFactory.createAvroSerializer<VerificationResponse> {  }

    override val commandType = ProcessMemberVerificationRequest::class.java

    override fun invoke(state: RegistrationState?, key: String, command: ProcessMemberVerificationRequest): RegistrationHandlerResult {
        val responseTimestamp = clock.instant()
        val mgm = command.source
        val member = command.destination
        val authenticatedMessageHeader = AuthenticatedMessageHeader(
            // we need to switch here the source and destination
            mgm,
            member,
            responseTimestamp.plusMillis(TTL),
            UUID.randomUUID().toString(),
            null,
            MEMBERSHIP_P2P_SUBSYSTEM
        )
        val authenticatedMessage = AuthenticatedMessage(
            authenticatedMessageHeader,
            ByteBuffer.wrap(
                responseSerializer.serialize(
                    VerificationResponse(
                        command.verificationRequest.registrationId,
                        KeyValuePairList(emptyList<KeyValuePair>())
                    )
                )
            )
        )
        return RegistrationHandlerResult(
            null,
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