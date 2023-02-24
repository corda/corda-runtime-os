package net.corda.membership.impl.p2p.handler

import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.ProcessMemberVerificationResponse
import net.corda.data.membership.p2p.VerificationResponse
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.REGISTRATION_COMMAND_TOPIC
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.schema.registry.deserialize
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

internal class VerificationResponseHandler(
    private val avroSchemaRegistry: AvroSchemaRegistry
) : AuthenticatedMessageHandler() {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun invokeAuthenticatedMessage(
        header: AuthenticatedMessageHeader,
        payload: ByteBuffer
    ): Record<String, RegistrationCommand> {
        logger.info("Received verification response from ${header.source}. Sending it to RegistrationManagementService to process.")
        val response = avroSchemaRegistry.deserialize<VerificationResponse>(payload)
        val registrationId = response.registrationId
        return Record(
            REGISTRATION_COMMAND_TOPIC,
            "$registrationId-${header.destination.toCorda().shortHash}",
            RegistrationCommand(
                ProcessMemberVerificationResponse(
                    response
                )
            )
        )
    }
}
