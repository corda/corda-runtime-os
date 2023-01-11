package net.corda.membership.impl.p2p.handler

import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.member.ProcessMemberVerificationRequest
import net.corda.data.membership.p2p.VerificationRequest
import net.corda.messaging.api.records.Record
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.schema.Schemas.Membership.Companion.REGISTRATION_COMMAND_TOPIC
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.schema.registry.deserialize
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.toCorda
import java.nio.ByteBuffer

internal class VerificationRequestHandler(
    private val avroSchemaRegistry: AvroSchemaRegistry
) : AuthenticatedMessageHandler()  {
    companion object {
        private val logger = contextLogger()
    }

    override fun invokeAuthenticatedMessage(
        header: AuthenticatedMessageHeader,
        payload: ByteBuffer
    ): Record<String, RegistrationCommand> {
        logger.info("Received verification request from ${header.source}. Sending it to RegistrationManagementService to process.")
        val request = avroSchemaRegistry.deserialize<VerificationRequest>(payload)
        val registrationId = request.registrationId
        return Record(
            REGISTRATION_COMMAND_TOPIC,
            "$registrationId-${header.destination.toCorda().shortHash}",
            RegistrationCommand(
                ProcessMemberVerificationRequest(
                    header.destination,
                    header.source,
                    request
                )
            )
        )
    }
}
