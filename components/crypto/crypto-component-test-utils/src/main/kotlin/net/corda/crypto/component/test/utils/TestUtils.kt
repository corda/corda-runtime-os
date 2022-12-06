package net.corda.crypto.component.test.utils

import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.registry.LifecycleRegistry
import net.corda.lifecycle.test.impl.TestLifecycleCoordinatorFactoryImpl
import net.corda.messaging.api.publisher.RPCSender
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.SignatureSpec
import org.assertj.core.api.Assertions.assertThat
import org.slf4j.Logger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.time.Instant

inline fun <reified RESULT: Any> RPCSender<*, *>.act(
    block: () -> RESULT
): SendActResult<RESULT> {
    val result = actWithTimer(block)
    return SendActResult(
        before = result.first,
        after = result.second,
        value = result.third
    )
}

inline fun <reified R> actWithTimer(block: () -> R): Triple<Instant, Instant, R> {
    val before = Instant.now()
    val result = block()
    val after = Instant.now()
    return Triple(before, after, result)
}

fun assertThatIsBetween(actual: Instant, before: Instant, after: Instant) {
    assertThat(actual.toEpochMilli())
        .isGreaterThanOrEqualTo(before.toEpochMilli())
        .isLessThanOrEqualTo(after.toEpochMilli())
}

data class SendActResult<RESPONSE>(
    val before: Instant,
    val after: Instant,
    val value: RESPONSE
) {
    fun assertThatIsBetween(timestamp: Instant) = assertThatIsBetween(timestamp, before, after)
}

fun generateKeyPair(schemeMetadata: CipherSchemeMetadata, schemeName: String): KeyPair {
    val scheme = schemeMetadata.findKeyScheme(schemeName)
    val keyPairGenerator = KeyPairGenerator.getInstance(
        scheme.algorithmName,
        schemeMetadata.providers.getValue(scheme.providerName)
    )
    if (scheme.algSpec != null) {
        keyPairGenerator.initialize(scheme.algSpec, schemeMetadata.secureRandom)
    } else if (scheme.keySize != null) {
        keyPairGenerator.initialize(scheme.keySize!!, schemeMetadata.secureRandom)
    }
    return keyPairGenerator.generateKeyPair()
}

fun signData(
    schemeMetadata: CipherSchemeMetadata,
    signatureSpec: SignatureSpec,
    keyPair: KeyPair,
    data: ByteArray
): ByteArray {
    val scheme = schemeMetadata.findKeyScheme(keyPair.public)
    val signature = Signature.getInstance(
        signatureSpec.signatureName,
        schemeMetadata.providers[scheme.providerName]
    )
    signature.initSign(keyPair.private, schemeMetadata.secureRandom)
    signature.update(data)
    return signature.sign()
}

fun TestLifecycleCoordinatorFactoryImpl.reportDownComponents(logger: Logger): String {
    val downReport = (registry as LifecycleRegistry).componentStatus().values.filter {
        it.status == LifecycleStatus.DOWN
    }.sortedBy {
        it.name.componentName
    }.joinToString(",${System.lineSeparator()}") {
        "${it.name.componentName}=${it.status}"
    }
    val message = "LIFECYCLE COMPONENTS STILL DOWN: [${System.lineSeparator()}$downReport${System.lineSeparator()}]"
    logger.warn(message)
    return message
}