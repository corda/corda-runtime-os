package net.corda.membership.lib

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.PersistentGroupParameters
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import java.nio.ByteBuffer
import net.corda.data.membership.SignedGroupParameters as AvroSignedGroupParameters

fun InternalGroupParameters.toPersistentGroupParameters(
    owner: HoldingIdentity,
    keyEncodingService: KeyEncodingService
): PersistentGroupParameters {
    val signed = this as? SignedGroupParameters
    return PersistentGroupParameters(
        owner.toAvro(),
        AvroSignedGroupParameters(
            ByteBuffer.wrap(groupParameters),
            signed?.let {
                CryptoSignatureWithKey(
                    ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(it.mgmSignature.by)),
                    ByteBuffer.wrap(it.mgmSignature.bytes)
                )
            },
            signed?.let {
                CryptoSignatureSpec(it.mgmSignatureSpec.signatureName, null, null)
            }
        )
    )
}
