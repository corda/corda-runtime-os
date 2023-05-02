package net.corda.membership.impl.registration.dynamic.mgm

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.SignedData
import net.corda.data.membership.common.RegistrationStatus
import net.corda.membership.lib.SignedMemberInfo
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.lib.toWire
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.registration.InvalidMembershipRegistrationException
import net.corda.virtualnode.HoldingIdentity
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.UUID

internal class MGMRegistrationRequestHandler(
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val membershipPersistenceClient: MembershipPersistenceClient,
    private val membershipQueryClient: MembershipQueryClient,
) {

    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val keyValuePairListSerializer =
        cordaAvroSerializationFactory.createAvroSerializer<KeyValuePairList> {
            logger.error("Failed to serialize key value pair list.")
        }

    fun persistRegistrationRequest(
        registrationId: UUID,
        holdingIdentity: HoldingIdentity,
        mgmInfo: SignedMemberInfo
    ) {
        val serializedMemberContext = serialize(mgmInfo.memberInfo.memberProvidedContext.toWire())
        val serializedRegistrationContext = keyValuePairListSerializer.serialize(KeyValuePairList(emptyList()))

        val registrationRequestPersistenceResult = membershipPersistenceClient.persistRegistrationRequest(
            viewOwningIdentity = holdingIdentity,
            registrationRequest = RegistrationRequest(
                status = RegistrationStatus.APPROVED,
                registrationId = registrationId.toString(),
                requester = holdingIdentity,
                memberContext = SignedData(
                    ByteBuffer.wrap(serializedMemberContext),
                    mgmInfo.memberSignature,
                    mgmInfo.memberSignatureSpec
                ),
                registrationContext = SignedData(
                    ByteBuffer.wrap(serializedRegistrationContext),
                    CryptoSignatureWithKey(
                        ByteBuffer.wrap(byteArrayOf()),
                        ByteBuffer.wrap(byteArrayOf())
                    ),
                    CryptoSignatureSpec("", null, null)
                ),
                serial = 0L,
            )
        ).execute()
        if (registrationRequestPersistenceResult is MembershipPersistenceResult.Failure) {
            throw InvalidMembershipRegistrationException(
                "Registration failed, persistence error. Reason: ${registrationRequestPersistenceResult.errorMsg}"
            )
        }
    }

    fun throwIfRegistrationAlreadyApproved(holdingIdentity: HoldingIdentity) {
        val result = membershipQueryClient.queryRegistrationRequests(holdingIdentity).getOrThrow()
        result.find { it.registrationStatus == RegistrationStatus.APPROVED }?.let { approvedRegistration ->
            throw InvalidMembershipRegistrationException(
                "Registration failed, there is already an approved registration for" +
                        " ${holdingIdentity.shortHash} with id ${approvedRegistration.registrationId}."
            )
        }
    }

    private fun serialize(data: KeyValuePairList) = keyValuePairListSerializer.serialize(data)
        ?: throw InvalidMembershipRegistrationException(
            "Failed to serialize the member context for this request."
        )
}