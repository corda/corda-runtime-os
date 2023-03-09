package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroDeserializer
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.SignedGroupParameters
import net.corda.membership.datamodel.GroupParametersEntity
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import java.nio.ByteBuffer

fun ByteArray.wrap() = ByteBuffer.wrap(this)

fun GroupParametersEntity.toSignedParameters(
    deserializer: CordaAvroDeserializer<KeyValuePairList>
) = SignedGroupParameters(
        parameters.wrap(),
        CryptoSignatureWithKey(
            signaturePublicKey.wrap(),
            signatureContent.wrap(),
            deserializer.deserializeKeyValuePairList(signatureContext)
        )
    )


fun CordaAvroDeserializer<KeyValuePairList>.deserializeKeyValuePairList(
    content: ByteArray
): KeyValuePairList {
    return deserialize(content) ?: throw MembershipPersistenceException(
        "Failed to deserialize key value pair list B."
    )
}