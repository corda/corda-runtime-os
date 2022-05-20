@file:JvmName("WireUtils")

package net.corda.crypto.impl

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoRequestContext
import net.corda.data.crypto.wire.CryptoSignatureParameterSpec
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.v5.cipher.suite.AlgorithmParameterSpecEncodingService
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.schemes.SerializedAlgorithmParameterSpec
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SignatureSpec
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID

val emptyKeyValuePairList = KeyValuePairList(emptyList())

inline fun <reified CALLER> createWireRequestContext(
    tenantId: String,
    other: KeyValuePairList = emptyKeyValuePairList
): CryptoRequestContext {
    return CryptoRequestContext(
        CALLER::class.simpleName,
        Instant.now(),
        UUID.randomUUID().toString(),
        tenantId,
        other
    )
}

fun Map<String, String>.toWire(): KeyValuePairList {
    return KeyValuePairList(
        map {
            KeyValuePair(it.key, it.value)
        }
    )
}


fun KeyValuePairList.toMap() : Map<String, String> = items.toMap()

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

fun SignatureSpec.toWire(serializer: AlgorithmParameterSpecEncodingService) = CryptoSignatureSpec(
    signatureName,
    customDigestName?.name,
    if (params != null) {
        val params = serializer.serialize(params!!)
        CryptoSignatureParameterSpec(params.clazz, ByteBuffer.wrap(params.bytes))
    } else {
        null
    }
)