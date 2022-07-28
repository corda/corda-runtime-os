package net.corda.membership.impl.p2p.handler

import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.StartRegistration
import net.corda.data.membership.p2p.MembershipRegistrationRequest
import net.corda.messaging.api.records.Record
import net.corda.p2p.app.UnauthenticatedMessageHeader
import net.corda.schema.Schemas.Membership.Companion.REGISTRATION_COMMAND_TOPIC
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.schema.registry.deserialize
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.toCorda
import java.nio.ByteBuffer

internal class RegistrationRequestHandler(
    private val avroSchemaRegistry: AvroSchemaRegistry
) : UnauthenticatedMessageHandler() {
    companion object {
        private val logger = contextLogger()
    }

    override fun invokeUnauthenticatedMessage(
        header: UnauthenticatedMessageHeader,
        payload: ByteBuffer
    ): Record<String, RegistrationCommand> {
        logger.info("Received registration request. Issuing StartRegistration command.")
        val registrationRequest = avroSchemaRegistry.deserialize<MembershipRegistrationRequest>(payload)
        val registrationId = registrationRequest.registrationId
        return Record(
            REGISTRATION_COMMAND_TOPIC,
            registrationId + "-" + header.destination.toCorda().shortHash,
            RegistrationCommand(
                StartRegistration(
                    header.destination,
                    header.source,
                    registrationRequest
                )
            )
        )
    }
}
