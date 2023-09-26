package net.corda.membership.impl.p2p.handler

import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.ProcessMemberVerificationResponse
import net.corda.data.membership.p2p.VerificationResponse
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.REGISTRATION_COMMAND_TOPIC
import net.corda.schema.registry.AvroSchemaRegistry
import org.slf4j.LoggerFactory

internal class VerificationResponseHandler(
    avroSchemaRegistry: AvroSchemaRegistry
) : AuthenticatedMessageHandler<VerificationResponse>(avroSchemaRegistry) {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val payloadType = VerificationResponse::class.java

    override fun invokeAuthenticatedMessage(
        header: AuthenticatedMessageHeader,
        payload: VerificationResponse
    ): Record<String, RegistrationCommand> {
        logger.info(
            "Received verification response with message ID ${header.messageId} and trace ID ${header.traceId} from ${header.source}. " +
                    "Sending it to RegistrationManagementService to process."
        )
        return Record(
            REGISTRATION_COMMAND_TOPIC,
            "${header.source.x500Name}-${header.source.groupId}",
            RegistrationCommand(
                ProcessMemberVerificationResponse(
                    payload
                )
            )
        )
    }
}
