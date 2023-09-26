package net.corda.membership.impl.p2p.handler

import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.member.ProcessMemberVerificationRequest
import net.corda.data.membership.p2p.VerificationRequest
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.REGISTRATION_COMMAND_TOPIC
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory

internal class VerificationRequestHandler(
    avroSchemaRegistry: AvroSchemaRegistry
) : AuthenticatedMessageHandler<VerificationRequest>(avroSchemaRegistry)  {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val payloadType = VerificationRequest::class.java

    override fun invokeAuthenticatedMessage(
        header: AuthenticatedMessageHeader,
        payload: VerificationRequest
    ): Record<String, RegistrationCommand> {
        logger.info("Received verification request from ${header.source}. Sending it to RegistrationManagementService to process.")
        val registrationId = payload.registrationId
        return Record(
            REGISTRATION_COMMAND_TOPIC,
            "$registrationId-${header.destination.toCorda().shortHash}",
            RegistrationCommand(
                ProcessMemberVerificationRequest(
                    header.destination,
                    header.source,
                    payload
                )
            )
        )
    }
}
