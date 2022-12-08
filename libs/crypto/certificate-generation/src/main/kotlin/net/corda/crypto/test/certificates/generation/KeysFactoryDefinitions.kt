package net.corda.crypto.test.certificates.generation

import net.corda.crypto.cipher.suite.schemes.KeySchemeTemplate
import net.corda.crypto.test.certificates.generation.Algorithm.Companion.toAlgorithm
import java.security.InvalidParameterException
import java.security.spec.AlgorithmParameterSpec

enum class Algorithm {
    RSA,
    EC;
    companion object {
        fun String.toAlgorithm(): Algorithm {
            if (this.equals("RSA", ignoreCase = true)) {
                return RSA
            }
            if (this.equals("EC", ignoreCase = true)) {
                return EC
            }
            if (this.equals("ECDSA", ignoreCase = true)) {
                return EC
            }
            throw InvalidParameterException("Algorithm $this is not supported")
        }
    }
}

/**
 * Define a keys factory definition.
 *
 * @param algorithm - The algorithm name
 * @param keySize - The keys size
 * @param spec - The keys spec.
 */
data class KeysFactoryDefinitions(
    val algorithm: Algorithm,
    val keySize: Int?,
    val spec: AlgorithmParameterSpec?
)

/**
 * Convert a [KeySchemeTemplate] into a [KeysFactoryDefinitions]s
 */
fun KeySchemeTemplate.toFactoryDefinitions(): KeysFactoryDefinitions {
    return KeysFactoryDefinitions(
        this.algorithmName.toAlgorithm(),
        this.keySize,
        this.algSpec,
    )
}
