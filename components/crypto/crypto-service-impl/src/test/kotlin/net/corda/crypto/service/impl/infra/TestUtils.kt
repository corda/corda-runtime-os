package net.corda.crypto.service.impl.infra

import net.corda.v5.cipher.suite.CipherSchemeMetadata
import org.assertj.core.api.Assertions.assertThat
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.time.Instant

fun generateKeyPair(schemeMetadata: CipherSchemeMetadata, signatureSchemeName: String): KeyPair {
    val scheme = schemeMetadata.findSignatureScheme(signatureSchemeName)
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

fun assertThatIsBetween(actual: Instant, before: Instant, after: Instant) {
    assertThat(actual.toEpochMilli())
        .isGreaterThanOrEqualTo(before.toEpochMilli())
        .isLessThanOrEqualTo(after.toEpochMilli())
}

inline fun <reified RESULT: Any> act(block: () -> RESULT?): ActResult<RESULT> {
    val before = Instant.now()
    val result = block()
    val after = Instant.now()
    return ActResult(
        before = before,
        after = after,
        value = result
    )
}

open class ActResultTimestamps(
    val before: Instant,
    val after: Instant,
) {
    fun assertThatIsBetween(timestamp: Instant) = assertThatIsBetween(timestamp, before, after)
}

class ActResult<RESULT>(
    before: Instant,
    after: Instant,
    val value: RESULT?
) : ActResultTimestamps(before, after)