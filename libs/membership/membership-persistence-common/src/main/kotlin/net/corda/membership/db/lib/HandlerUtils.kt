package net.corda.membership.db.lib

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.SignedGroupParameters
import net.corda.data.membership.common.RegistrationRequestDetails
import net.corda.data.membership.preauth.PreAuthToken
import net.corda.data.membership.preauth.PreAuthTokenStatus
import net.corda.membership.datamodel.GroupParametersEntity
import net.corda.membership.datamodel.PreAuthTokenEntity
import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.membership.db.lib.RegistrationStatusHelper.toStatus
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import java.nio.ByteBuffer

private const val DEFAULT_REGISTRATION_PROTOCOL_VERSION = 1

fun PreAuthTokenEntity.toAvro(): PreAuthToken {
    return PreAuthToken(
        this.tokenId,
        this.ownerX500Name,
        this.ttl,
        PreAuthTokenStatus.valueOf(this.status),
        this.creationRemark,
        this.removalRemark,
    )
}

fun GroupParametersEntity.toAvro() = if (!isSigned()) {
    SignedGroupParameters(ByteBuffer.wrap(parameters), null, null)
} else {
    SignedGroupParameters(
        ByteBuffer.wrap(parameters),
        CryptoSignatureWithKey(
            ByteBuffer.wrap(signaturePublicKey!!),
            ByteBuffer.wrap(signatureContent!!),
        ),
        CryptoSignatureSpec(signatureSpec, null, null),
    )
}

fun CordaAvroDeserializer<KeyValuePairList>.deserializeKeyValuePairList(
    content: ByteArray,
): KeyValuePairList {
    return deserialize(content) ?: throw MembershipPersistenceException(
        "Failed to deserialize key value pair list.",
    )
}
fun CordaAvroSerializer<KeyValuePairList>.serializeKeyValuePairList(
    content: KeyValuePairList,
): ByteArray {
    return serialize(content) ?: throw MembershipPersistenceException(
        "Failed to serialize key value pair list.",
    )
}
fun retrieveSignatureSpec(signatureSpec: String) = if (signatureSpec.isEmpty()) {
    CryptoSignatureSpec("", null, null)
} else {
    CryptoSignatureSpec(signatureSpec, null, null)
}

fun RegistrationRequestEntity.toDetails(
    keyValuePairListDeserializer: CordaAvroDeserializer<KeyValuePairList>,
): RegistrationRequestDetails {
    val memberContext = keyValuePairListDeserializer.deserialize(this.memberContext)
    val registrationProtocolVersion = memberContext?.items?.firstOrNull {
        it.key == "registrationProtocolVersion"
    }?.value?.toIntOrNull() ?: DEFAULT_REGISTRATION_PROTOCOL_VERSION
    return RegistrationRequestDetails.newBuilder()
        .setRegistrationSent(this.created)
        .setRegistrationLastModified(this.lastModified)
        .setRegistrationStatus(this.status.toStatus())
        .setRegistrationId(this.registrationId)
        .setRegistrationProtocolVersion(registrationProtocolVersion)
        .setMemberProvidedContext(memberContext)
        .setMemberSignature(
            CryptoSignatureWithKey(
                ByteBuffer.wrap(this.memberContextSignatureKey),
                ByteBuffer.wrap(this.memberContextSignatureContent),
            ),
        )
        .setMemberSignatureSpec(retrieveSignatureSpec(this.memberContextSignatureSpec))
        .setRegistrationContext(keyValuePairListDeserializer.deserialize(registrationContext))
        .setRegistrationContextSignature(
            CryptoSignatureWithKey(
                ByteBuffer.wrap(registrationContextSignatureKey),
                ByteBuffer.wrap(registrationContextSignatureContent),
            ),
        )
        .setRegistrationContextSignatureSpec(retrieveSignatureSpec(registrationContextSignatureSpec))
        .setReason(this.reason)
        .setSerial(this.serial)
        .build()
}
