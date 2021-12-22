package net.corda.crypto.client

import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import org.bouncycastle.jce.ECNamedCurveTable
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.lessThanOrEqualTo
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.time.Instant

fun generateKeyPair(schemeMetadata: CipherSchemeMetadata): KeyPair {
    val keyPairGenerator = KeyPairGenerator.getInstance("EC", schemeMetadata.providers["BC"])
    keyPairGenerator.initialize(ECNamedCurveTable.getParameterSpec("secp256r1"))
    return keyPairGenerator.generateKeyPair()
}

fun sign(schemeMetadata: CipherSchemeMetadata, privateKey: PrivateKey, data: ByteArray): ByteArray {
    val signature = Signature.getInstance("SHA256withECDSA", schemeMetadata.providers["BC"])
    signature.initSign(privateKey, schemeMetadata.secureRandom)
    signature.update(data)
    return signature.sign()
}

inline fun <reified R> Publisher.act(
    block: () -> R
): PublishActResult<R> {
    val result = actWithTimer(block)
    val messages = argumentCaptor<List<Record<*, *>>>()
    verify(this).publish(messages.capture())
    return PublishActResult(
        before = result.first,
        after = result.second,
        value = result.third,
        messages = messages.allValues
    )
}

inline fun <reified R> actWithTimer(block: () -> R): Triple<Instant, Instant, R> {
    val before = Instant.now()
    val result = block()
    val after = Instant.now()
    return Triple(before, after, result)
}

fun assertThatIsBetween(actual: Instant, before: Instant, after: Instant) {
    assertThat(
        actual.toEpochMilli(),
        allOf(
            greaterThanOrEqualTo(before.toEpochMilli()),
            lessThanOrEqualTo(after.toEpochMilli())
        )
    )
}

data class PublishActResult<R>(
    val before: Instant,
    val after: Instant,
    val value: R,
    val messages: List<List<Record<*, *>>>
) {
    val firstRecord: Record<*, *> get() = messages.first()[0]

    fun assertThatIsBetween(timestamp: Instant) = assertThatIsBetween(timestamp, before, after)
}