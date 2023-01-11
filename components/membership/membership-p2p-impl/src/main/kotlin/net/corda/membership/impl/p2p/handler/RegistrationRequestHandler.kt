package net.corda.membership.impl.p2p.handler

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.hes.StableKeyPairDecryptor
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.StartRegistration
import net.corda.data.membership.p2p.MembershipRegistrationRequest
import net.corda.data.membership.p2p.UnauthenticatedRegistrationRequest
import net.corda.membership.lib.MemberInfoExtension.Companion.ecdhKey
import net.corda.membership.lib.MemberInfoExtension.Companion.isMgm
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.records.Record
import net.corda.data.p2p.app.UnauthenticatedMessageHeader
import net.corda.schema.Schemas.Membership.Companion.REGISTRATION_COMMAND_TOPIC
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.schema.registry.deserialize
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import java.nio.ByteBuffer
import java.security.PublicKey

internal class RegistrationRequestHandler(
    private val avroSchemaRegistry: AvroSchemaRegistry,
    private val stableKeyPairDecryptor: StableKeyPairDecryptor,
    private val keyEncodingService: KeyEncodingService,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
) : UnauthenticatedMessageHandler() {
    companion object {
        private val logger = contextLogger()
    }

    override fun invokeUnauthenticatedMessage(
        header: UnauthenticatedMessageHeader,
        payload: ByteBuffer
    ): Record<String, RegistrationCommand>? {
        try {
            logger.info("Received registration request. Issuing StartRegistration command.")
            val registrationRequest = avroSchemaRegistry.deserialize<MembershipRegistrationRequest>(
                ByteBuffer.wrap(decryptPayload(payload, header.destination.toCorda()))
            )
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
        } catch (e: Exception) {
            logger.warn("Could not create start registration command. Reason: ${e.message}", e)
            return null
        }
    }

    /** Decrypts the received encrypted registration request received from member. */
    private fun decryptPayload(payload: ByteBuffer, mgm: HoldingIdentity): ByteArray {
        val request = avroSchemaRegistry.deserialize<UnauthenticatedRegistrationRequest>(payload)
        val reqHeader = request.header
        val memberKey = keyEncodingService.decodePublicKey(reqHeader.key)
        val mgmKey = getECDHKey(mgm)
        return stableKeyPairDecryptor.decrypt(
            mgm.shortHash.value,
            reqHeader.salt.array(),
            mgmKey,
            memberKey,
            request.payload.array(),
            reqHeader.aad.array()
        )
    }

    /** Retrieves the MGM's ECDH key required for decrypting the message. */
    private fun getECDHKey(mgm: HoldingIdentity): PublicKey {
        val mgmInfo = membershipGroupReaderProvider.getGroupReader(mgm).lookup(mgm.x500Name)
            ?: throw IllegalArgumentException("Could not find member info for ${mgm.x500Name}.")
        require(mgmInfo.isMgm) { "Destination ${mgm.x500Name} of registration request was not an MGM." }
        return mgmInfo.ecdhKey ?: throw IllegalArgumentException("MGM's ECDH key is missing.")
    }
}
