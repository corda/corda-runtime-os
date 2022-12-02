@file:JvmName("WireUtils")

package net.corda.crypto.impl

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoRequestContext
import net.corda.data.crypto.wire.CryptoSignatureParameterSpec
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.crypto.cipher.suite.AlgorithmParameterSpecEncodingService
import net.corda.v5.cipher.suite.CustomSignatureSpec
import net.corda.v5.cipher.suite.schemes.SerializedAlgorithmParameterSpec
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.ParameterizedSignatureSpec
import net.corda.v5.crypto.SignatureSpec
import java.nio.ByteBuffer
import java.security.spec.AlgorithmParameterSpec
import java.time.Instant

val emptyKeyValuePairList = KeyValuePairList(emptyList())

inline fun <reified CALLER> createWireRequestContext(
    requestId: String,
    tenantId: String,
    other: KeyValuePairList = emptyKeyValuePairList
): CryptoRequestContext = createWireRequestContext(CALLER::class.java.simpleName, requestId, tenantId, other)

fun createWireRequestContext(
    caller: String,
    requestId: String,
    tenantId: String,
    other: KeyValuePairList = emptyKeyValuePairList
): CryptoRequestContext {
    return CryptoRequestContext(
        caller,
        Instant.now(),
        requestId,
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


fun KeyValuePairList.toMap(): Map<String, String> = items.toMap()

fun List<KeyValuePair>.toMap(): Map<String, String> {
    val map = mutableMapOf<String, String>()
    forEach {
        map[it.key] = it.value
    }
    return map
}

fun CryptoSignatureSpec.toSignatureSpec(serializer: net.corda.crypto.cipher.suite.AlgorithmParameterSpecEncodingService): SignatureSpec {
    val algorithmParams = if (params != null) {
        serializer.deserialize(
            SerializedAlgorithmParameterSpec(
                clazz = params.className,
                bytes = params.bytes.array()
            )
        )
    } else {
        null
    }
    return when {
        !customDigestName.isNullOrBlank() -> {
            CustomSignatureSpec(signatureName, DigestAlgorithmName(customDigestName), algorithmParams)
        }
        algorithmParams != null -> ParameterizedSignatureSpec(signatureName, algorithmParams)
        else -> SignatureSpec(signatureName)
    }
}

fun SignatureSpec.toWire(serializer: net.corda.crypto.cipher.suite.AlgorithmParameterSpecEncodingService): CryptoSignatureSpec =
    when(this) {
        is CustomSignatureSpec -> CryptoSignatureSpec(
            signatureName,
            customDigestName.name,
            params?.serialize(serializer)
        )
        is ParameterizedSignatureSpec -> CryptoSignatureSpec(
            signatureName,
            null,
            params.serialize(serializer)
        )
        else -> CryptoSignatureSpec(signatureName, null, null)
    }

private fun AlgorithmParameterSpec.serialize(
    serializer: net.corda.crypto.cipher.suite.AlgorithmParameterSpecEncodingService
): CryptoSignatureParameterSpec {
    val params = serializer.serialize(this)
    return CryptoSignatureParameterSpec(params.clazz, ByteBuffer.wrap(params.bytes))
}
