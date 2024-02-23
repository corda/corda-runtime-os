@file:JvmName("WireUtils")

package net.corda.crypto.impl

import io.micrometer.core.instrument.Timer
import net.corda.crypto.cipher.suite.AlgorithmParameterSpecEncodingService
import net.corda.crypto.cipher.suite.CustomSignatureSpec
import net.corda.crypto.cipher.suite.ParameterizedSignatureSpec
import net.corda.crypto.cipher.suite.SignatureSpecImpl
import net.corda.crypto.cipher.suite.schemes.SerializedAlgorithmParameterSpec
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoRequestContext
import net.corda.data.crypto.wire.CryptoSignatureParameterSpec
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.metrics.CordaMetrics
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SignatureSpec
import java.nio.ByteBuffer
import java.security.spec.AlgorithmParameterSpec
import java.time.Duration
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

private const val TO_SIGNATURE_SPEC_OPERATION_NAME = "toSignatureSpec"
private const val TO_WIRE_OPERATION_NAME = "toWire"

fun CryptoSignatureSpec.toSignatureSpec(serializer: AlgorithmParameterSpecEncodingService): SignatureSpec {
    val startTime = System.nanoTime()
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
        else -> SignatureSpecImpl(signatureName)
    }.also {
        CordaMetrics.Metric.Crypto.SignatureSpecTimer.builder()
            .withTag(CordaMetrics.Tag.OperationName, TO_SIGNATURE_SPEC_OPERATION_NAME)
            .build<Timer>()
            .record(Duration.ofNanos(System.nanoTime() - startTime))
    }
}


fun SignatureSpec.toWire(serializer: AlgorithmParameterSpecEncodingService): CryptoSignatureSpec {
    val startTime = System.nanoTime()
    return when (this) {
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
    }.also {
        CordaMetrics.Metric.Crypto.SignatureSpecTimer.builder()
            .withTag(CordaMetrics.Tag.OperationName, TO_WIRE_OPERATION_NAME)
            .build()
            .record(Duration.ofNanos(System.nanoTime() - startTime))
    }
}

private fun AlgorithmParameterSpec.serialize(
    serializer: AlgorithmParameterSpecEncodingService
): CryptoSignatureParameterSpec {
    val params = serializer.serialize(this)
    return CryptoSignatureParameterSpec(params.clazz, ByteBuffer.wrap(params.bytes))
}
