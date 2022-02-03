@file:JvmName("WireUtils")

package net.corda.crypto.service.impl

import net.corda.data.KeyValuePair
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.v5.cipher.suite.AlgorithmParameterSpecEncodingService
import net.corda.v5.cipher.suite.schemes.SerializedAlgorithmParameterSpec
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SignatureSpec

fun List<KeyValuePair>.toMap() : Map<String, String> {
    val map = mutableMapOf<String, String>()
    forEach {
        map[it.key] = it.value
    }
    return map
}

fun CryptoSignatureSpec.toSignatureSpec(serializer: AlgorithmParameterSpecEncodingService) = SignatureSpec(
    signatureName = signatureName,
    customDigestName = if (customDigestName.isNullOrBlank()) {
        null
    } else {
        DigestAlgorithmName(customDigestName)
    },
    params = if(params != null) {
        serializer.deserialize(
            SerializedAlgorithmParameterSpec(
                clazz = params.className,
                bytes = params.bytes.array()
            )
        )
    } else {
        null
    }
)
