@file:JvmName("WireUtils")

package net.corda.crypto.impl

import net.corda.crypto.core.service.AlgorithmParameterSpecEncodingService
import net.corda.crypto.core.service.SerializedAlgorithmParameterSpec
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoRequestContext
import net.corda.data.crypto.wire.CryptoSignatureParameterSpec
import net.corda.data.crypto.wire.CryptoSignatureSpec
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

fun CryptoSignatureSpec.toSignatureSpec(serializer: AlgorithmParameterSpecEncodingService): SignatureSpec {
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
        algorithmParams != null -> ParameterizedSignatureSpec(signatureName, algorithmParams)
        else -> SignatureSpec(signatureName)
    }
}

fun SignatureSpec.toWire(serializer: AlgorithmParameterSpecEncodingService): CryptoSignatureSpec =
    when(this) {
        is ParameterizedSignatureSpec -> CryptoSignatureSpec(
            signatureName,
            null,
            params.serialize(serializer)
        )
        else -> CryptoSignatureSpec(signatureName, null, null)
    }

private fun AlgorithmParameterSpec.serialize(
    serializer: AlgorithmParameterSpecEncodingService
): CryptoSignatureParameterSpec {
    val params = serializer.serialize(this)
    return CryptoSignatureParameterSpec(params.clazz, ByteBuffer.wrap(params.bytes))
}
