package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroDeserializer
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.SignedGroupParameters
import net.corda.membership.datamodel.GroupParametersEntity
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import java.nio.ByteBuffer

fun GroupParametersEntity.toAvro(
    deserializer: CordaAvroDeserializer<KeyValuePairList>
) = SignedGroupParameters(
    ByteBuffer.wrap(parameters),
    if (isSigned()) {
        CryptoSignatureWithKey(
            ByteBuffer.wrap(signaturePublicKey!!),
            ByteBuffer.wrap(signatureContent!!),
            deserializer.deserializeKeyValuePairList(signatureContext!!)
        )
    } else null

)


fun CordaAvroDeserializer<KeyValuePairList>.deserializeKeyValuePairList(
    content: ByteArray
): KeyValuePairList {
    return deserialize(content) ?: throw MembershipPersistenceException(
        "Failed to deserialize key value pair list B."
    )
}