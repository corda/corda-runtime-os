package net.corda.membership.impl.p2p.handler

import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.member.PersistMemberRegistrationState
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.p2p.SetOwnRegistrationStatus
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.membership.lib.RegistrationStatusV2
import net.corda.membership.lib.SetOwnRegistrationStatusV2
import net.corda.messaging.api.records.Record
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
        val response = if (avroSchemaRegistry.getClassType(payload) == SetOwnRegistrationStatus::class.java) {
            avroSchemaRegistry.deserialize<SetOwnRegistrationStatus>(payload).convertToNewVersion()
        } else {
            avroSchemaRegistry.deserialize(payload)
        }
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

    private fun SetOwnRegistrationStatus.convertToNewVersion(): SetOwnRegistrationStatusV2 {
        val status = when (newStatus) {
            RegistrationStatus.NEW -> RegistrationStatusV2.NEW
            RegistrationStatus.SENT_TO_MGM -> RegistrationStatusV2.SENT_TO_MGM
            RegistrationStatus.RECEIVED_BY_MGM -> RegistrationStatusV2.RECEIVED_BY_MGM
            RegistrationStatus.PENDING_MEMBER_VERIFICATION -> RegistrationStatusV2.PENDING_MEMBER_VERIFICATION
            RegistrationStatus.PENDING_MANUAL_APPROVAL -> RegistrationStatusV2.PENDING_MANUAL_APPROVAL
            RegistrationStatus.PENDING_AUTO_APPROVAL -> RegistrationStatusV2.PENDING_AUTO_APPROVAL
            RegistrationStatus.APPROVED -> RegistrationStatusV2.APPROVED
            RegistrationStatus.DECLINED -> RegistrationStatusV2.DECLINED
            RegistrationStatus.INVALID -> RegistrationStatusV2.INVALID
            RegistrationStatus.FAILED -> RegistrationStatusV2.FAILED
            else -> throw IllegalArgumentException("Unknown status '${newStatus.name}' received.")
        }
        return SetOwnRegistrationStatusV2(registrationId, status, null)
    }
}
