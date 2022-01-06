package net.corda.crypto

import net.corda.v5.cipher.suite.CipherSchemeMetadata
import org.bouncycastle.jce.ECNamedCurveTable
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.time.Instant

fun generateKeyPair(schemeMetadata: CipherSchemeMetadata): KeyPair {
    val keyPairGenerator = KeyPairGenerator.getInstance("EC", schemeMetadata.providers["BC"])
    keyPairGenerator.initialize(ECNamedCurveTable.getParameterSpec("secp256r1"))
    return keyPairGenerator.generateKeyPair()
}

fun assertThatIsBetween(actual: Instant, before: Instant, after: Instant) {
    MatcherAssert.assertThat(
        actual.toEpochMilli(),
        Matchers.allOf(
            Matchers.greaterThanOrEqualTo(before.toEpochMilli()),
            Matchers.lessThanOrEqualTo(after.toEpochMilli())
        )
    )
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

data class ActResult<RESULT>(
    val before: Instant,
    val after: Instant,
    val value: RESULT?
) {
    fun assertThatIsBetween(timestamp: Instant) = assertThatIsBetween(timestamp, before, after)
}