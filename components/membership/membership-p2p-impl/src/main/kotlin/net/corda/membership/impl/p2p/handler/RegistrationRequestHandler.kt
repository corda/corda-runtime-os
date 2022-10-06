package net.corda.membership.impl.p2p.handler

import net.corda.crypto.ecies.StableKeyPairDecryptor
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.StartRegistration
import net.corda.data.membership.p2p.MembershipRegistrationRequest
import net.corda.data.membership.p2p.UnauthenticatedRegistrationRequest
import net.corda.data.membership.p2p.UnauthenticatedRegistrationRequestHeader
import net.corda.messaging.api.records.Record
import net.corda.p2p.app.UnauthenticatedMessageHeader
import net.corda.schema.Schemas.Membership.Companion.REGISTRATION_COMMAND_TOPIC
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.schema.registry.deserialize
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Reference
import java.nio.ByteBuffer

internal class RegistrationRequestHandler(
    private val avroSchemaRegistry: AvroSchemaRegistry,
    private val stableKeyPairDecryptor: StableKeyPairDecryptor,
    private val keyEncodingService: KeyEncodingService,
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
) : UnauthenticatedMessageHandler() {
    companion object {
        private val logger = contextLogger()
    }

    private val headerSerializer: CordaAvroSerializer<UnauthenticatedRegistrationRequestHeader> =
        cordaAvroSerializationFactory.createAvroSerializer { logger.error("Failed to serialize header.") }

    override fun invokeUnauthenticatedMessage(
        header: UnauthenticatedMessageHeader,
        payload: ByteBuffer
    ): Record<String, RegistrationCommand> {
        logger.info("Received registration request. Issuing StartRegistration command.")
        val request = avroSchemaRegistry.deserialize<UnauthenticatedRegistrationRequest>(payload)
        val reqheader = request.header
        val memberKey = keyEncodingService.decodePublicKey(request.key)
        val messageBytes = stableKeyPairDecryptor.decrypt(
            "mytenantid",
            "salt".toByteArray(),
            memberKey,
            memberKey, // should be the mgm ecdh key
            request.payload.array(),
            headerSerializer.serialize(reqheader)
        )
        val registrationRequest = avroSchemaRegistry.deserialize<MembershipRegistrationRequest>(ByteBuffer.wrap(messageBytes))
        val registrationId = registrationRequest.registrationId
        return Record(
            REGISTRATION_COMMAND_TOPIC,
            "$registrationId-${header.destination.toCorda().shortHash}",
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
