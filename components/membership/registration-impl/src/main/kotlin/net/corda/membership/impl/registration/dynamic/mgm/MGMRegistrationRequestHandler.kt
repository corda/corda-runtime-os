package net.corda.membership.impl.registration.dynamic.mgm

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.SignedData
import net.corda.data.membership.common.RegistrationRequestDetails
import net.corda.data.membership.common.v2.RegistrationStatus
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.lib.toWire
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.registration.InvalidMembershipRegistrationException
import net.corda.utilities.serialization.wrapWithNullErrorHandling
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

    private val signatureWithKey = CryptoSignatureWithKey(ByteBuffer.wrap(byteArrayOf()), ByteBuffer.wrap(byteArrayOf()))
    private val signatureSpec = CryptoSignatureSpec("", null, null)

    fun persistRegistrationRequest(
        registrationId: UUID,
        holdingIdentity: HoldingIdentity,
        context: Map<String, String>,
        serial: Long = 0L,
    ) {
        val serializedRegistrationContext = serialize(KeyValuePairList(emptyList()))
        val serializedMemberContext = serialize(context.toWire())

        val registrationRequestPersistenceResult = membershipPersistenceClient.persistRegistrationRequest(
            viewOwningIdentity = holdingIdentity,
            registrationRequest = RegistrationRequest(
                status = RegistrationStatus.APPROVED,
                registrationId = registrationId.toString(),
                requester = holdingIdentity,
                memberContext = SignedData(
                    ByteBuffer.wrap(serializedMemberContext),
                    signatureWithKey,
                    signatureSpec,
                ),
                registrationContext = SignedData(
                    ByteBuffer.wrap(serializedRegistrationContext),
                    signatureWithKey,
                    signatureSpec,
                ),
                serial = serial,
            ),
            create = false,
        ).execute()
        if (registrationRequestPersistenceResult is MembershipPersistenceResult.Failure) {
            throw InvalidMembershipRegistrationException(
                "Registration failed, persistence error. Reason: ${registrationRequestPersistenceResult.errorMsg}"
            )
        }
    }

    fun getLastRegistrationRequest(holdingIdentity: HoldingIdentity): RegistrationRequestDetails? {
        return membershipQueryClient.queryRegistrationRequests(
            viewOwningIdentity = holdingIdentity,
            requestSubjectX500Name = holdingIdentity.x500Name,
            statuses = listOf(RegistrationStatus.APPROVED),
        ).getOrThrow().sortedBy { it.serial }.lastOrNull()
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

    private fun serialize(data: KeyValuePairList) = wrapWithNullErrorHandling({
        InvalidMembershipRegistrationException("Failed to serialize the member context for this request.", it)
    }) {
        keyValuePairListSerializer.serialize(data)
    }
}
