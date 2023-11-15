package net.corda.membership.impl.p2p.handler

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.hes.StableKeyPairDecryptor
import net.corda.data.KeyValuePairList
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.QueueRegistration
import net.corda.data.membership.p2p.MembershipRegistrationRequest
import net.corda.data.membership.p2p.UnauthenticatedRegistrationRequest
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.MemberInfoExtension.Companion.ecdhKey
import net.corda.membership.lib.MemberInfoExtension.Companion.isMgm
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.REGISTRATION_COMMAND_TOPIC
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.schema.registry.deserialize
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.security.PublicKey

internal class RegistrationRequestHandler(
    private val avroSchemaRegistry: AvroSchemaRegistry,
    private val stableKeyPairDecryptor: StableKeyPairDecryptor,
    private val keyEncodingService: KeyEncodingService,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
) : UnauthenticatedMessageHandler() {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun invokeUnauthenticatedMessage(
        payload: ByteBuffer
    ): Record<String, RegistrationCommand>? {
        try {
            logger.info("Received registration request. Issuing QueueRegistration command.")
            val (registrationRequest, mgm) = decryptPayload(payload)
            val memberName = avroSchemaRegistry.deserialize<KeyValuePairList>(registrationRequest.memberContext.data)
                .items
                .firstOrNull { it.key == MemberInfoExtension.PARTY_NAME }
                ?.value
                ?: throw CordaRuntimeException("Invalid registration context - missing member name")
            val member = HoldingIdentity(
                MemberX500Name.parse(memberName),
                mgm.groupId,
            )
            logger.info("Registration request was from $memberName.")
            return Record(
                REGISTRATION_COMMAND_TOPIC,
                "${member.x500Name}-${member.groupId}",
                RegistrationCommand(
                    QueueRegistration(
                        mgm.toAvro(),
                        member.toAvro(),
                        registrationRequest,
                        0
                    )
                )
            )
        } catch (e: Exception) {
            logger.warn("Could not create QueueRegistration command. Reason: ${e.message}", e)
            return null
        }
    }

    /** Decrypts the received encrypted registration request received from member. */
    private fun decryptPayload(payload: ByteBuffer): Pair<MembershipRegistrationRequest, HoldingIdentity> {
        val request = avroSchemaRegistry.deserialize<UnauthenticatedRegistrationRequest>(payload)
        val reqHeader = request.header
        val mgm = reqHeader.mgm.toCorda()
        val memberKey = keyEncodingService.decodePublicKey(reqHeader.key)
        val mgmKey = getECDHKey(mgm)
        val requestPayload = stableKeyPairDecryptor.decrypt(
            mgm.shortHash.value,
            reqHeader.salt.array(),
            mgmKey,
            memberKey,
            request.payload.array(),
            reqHeader.aad.array(),
        )
        return avroSchemaRegistry.deserialize<MembershipRegistrationRequest>(
            ByteBuffer.wrap(requestPayload),
        ) to mgm
    }

    /** Retrieves the MGM's ECDH key required for decrypting the message. */
    private fun getECDHKey(mgm: HoldingIdentity): PublicKey {
        val mgmInfo = membershipGroupReaderProvider.getGroupReader(mgm).lookup(mgm.x500Name)
            ?: throw IllegalArgumentException("Could not find member info for ${mgm.x500Name}.")
        require(mgmInfo.isMgm) { "Destination ${mgm.x500Name} of registration request was not an MGM." }
        return mgmInfo.ecdhKey ?: throw IllegalArgumentException("MGM's ECDH key is missing.")
    }
}
