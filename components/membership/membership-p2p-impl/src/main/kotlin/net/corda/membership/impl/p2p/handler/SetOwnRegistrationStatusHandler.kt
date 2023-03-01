package net.corda.membership.impl.p2p.handler

import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.member.PersistMemberRegistrationState
import net.corda.data.membership.p2p.SetOwnRegistrationStatus
import net.corda.messaging.api.records.Record
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.schema.Schemas.Membership.REGISTRATION_COMMAND_TOPIC
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.schema.registry.deserialize
import net.corda.virtualnode.toCorda
import java.nio.ByteBuffer

internal class SetOwnRegistrationStatusHandler(
    private val avroSchemaRegistry: AvroSchemaRegistry
) : AuthenticatedMessageHandler() {
    override fun invokeAuthenticatedMessage(
        header: AuthenticatedMessageHeader,
        payload: ByteBuffer
    ): Record<String, RegistrationCommand> {
        val response = avroSchemaRegistry.deserialize<SetOwnRegistrationStatus>(payload)
        return Record(
            REGISTRATION_COMMAND_TOPIC,
            "${response.registrationId}-${response.newStatus}-${header.destination.toCorda().shortHash}",
            RegistrationCommand(
                PersistMemberRegistrationState(
                    header.destination,
                    response
                )
            )
        )
    }
}
