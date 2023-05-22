package net.corda.membership.impl.persistence.service.handler

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.SignedGroupParameters
import net.corda.membership.datamodel.GroupParametersEntity
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import java.nio.ByteBuffer

fun GroupParametersEntity.toAvro() = if (!isSigned()) {
    SignedGroupParameters(ByteBuffer.wrap(parameters), null, null)
} else {
    SignedGroupParameters(
        ByteBuffer.wrap(parameters),
        CryptoSignatureWithKey(
            ByteBuffer.wrap(signaturePublicKey!!),
            ByteBuffer.wrap(signatureContent!!)
        ),
        CryptoSignatureSpec(signatureSpec, null, null)
    )
}


fun CordaAvroDeserializer<KeyValuePairList>.deserializeKeyValuePairList(
    content: ByteArray
): KeyValuePairList {
    return deserialize(content) ?: throw MembershipPersistenceException(
        "Failed to deserialize key value pair list."
    )
}