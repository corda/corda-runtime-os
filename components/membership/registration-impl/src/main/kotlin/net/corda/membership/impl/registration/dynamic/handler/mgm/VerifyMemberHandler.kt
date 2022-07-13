package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.VerifyMember
import net.corda.data.membership.p2p.VerificationRequest
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
import java.nio.ByteBuffer
import java.util.UUID

class VerifyMemberHandler(
    cordaAvroSerializationFactory: CordaAvroSerializationFactory
) : RegistrationHandler {

    private companion object {
        val logger = contextLogger()
        val clock = UTCClock()
        const val MEMBERSHIP_P2P_SUBSYSTEM = "membership"
        const val TTL = 1000L
    }

    private val requestSerializer = cordaAvroSerializationFactory.createAvroSerializer<VerificationRequest> {  }

    override fun invoke(command: Record<String, RegistrationCommand>): RegistrationHandlerResult {
        logger.info("Handling request.")
        val inputCommand = command.value?.command as? VerifyMember
        require(inputCommand != null) {
            "Incorrect handler used for command of type ${command.value!!.command::class.java}"
        }
        val mgm = inputCommand.source
        val member = inputCommand.destination
        val registrationId = inputCommand.registrationId
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
        return RegistrationHandlerResult(
            RegistrationState(registrationId, member),
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