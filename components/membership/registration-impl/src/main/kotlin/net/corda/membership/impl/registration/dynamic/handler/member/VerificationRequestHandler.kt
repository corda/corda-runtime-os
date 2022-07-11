package net.corda.membership.impl.registration.dynamic.handler.member

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.command.registration.RegistrationCommand
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
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.toCorda
import java.nio.ByteBuffer
import java.util.UUID

class VerificationRequestHandler(
    cordaAvroSerializationFactory: CordaAvroSerializationFactory
) : RegistrationHandler {
    private companion object {
        val logger = contextLogger()
        val clock = UTCClock()
        const val MEMBERSHIP_P2P_SUBSYSTEM = "membership"
        const val TTL = 1000L
    }

    private val responseSerializer = cordaAvroSerializationFactory.createAvroSerializer<VerificationResponse> {  }

    override fun invoke(command: Record<String, RegistrationCommand>): RegistrationHandlerResult {
        logger.info("Handling request")
        val request = command.value?.command as? ProcessMemberVerificationRequest
        require(request != null) {
            "Incorrect handler used for command of type ${command.value!!.command::class.java}"
        }
        val responseTimestamp = clock.instant()
        val mgm = request.source
        val member = request.source
        val authenticatedMessageHeader = AuthenticatedMessageHeader(
            // we need to switch here the source and destination
            mgm,
            member,
            responseTimestamp.plusMillis(TTL)?.toEpochMilli(),
            UUID.randomUUID().toString(),
            null,
            MEMBERSHIP_P2P_SUBSYSTEM
        )
        val authenticatedMessage = AuthenticatedMessage(
            authenticatedMessageHeader,
            ByteBuffer.wrap(
                responseSerializer.serialize(
                    VerificationResponse(
                        request.verificationRequest.registrationId,
                        KeyValuePairList(emptyList<KeyValuePair>())
                    )
                )
            )
        )
        return RegistrationHandlerResult(
            RegistrationState(request.verificationRequest.registrationId, request.destination),
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