package net.corda.v5.cipher.suite

import net.corda.v5.cipher.suite.providers.encoding.KeyEncodingHandler
import net.corda.v5.cipher.suite.providers.verification.VerifySignatureHandler
import net.corda.v5.cipher.suite.scheme.KeyScheme
import net.corda.v5.crypto.extensions.DigestAlgorithmFactory
import java.security.PublicKey

interface AbstractCipherSuite {

    fun findKeyScheme(schemeCodeName: String): KeyScheme

    fun findKeyScheme(publicKey: PublicKey): KeyScheme

    fun findDigestAlgorithmFactory(algorithmName: String): DigestAlgorithmFactory?

    fun findVerifySignatureHandler(schemeCodeName: String): VerifySignatureHandler

    fun findKeyEncodingHandler(schemeCodeName: String): KeyEncodingHandler

    /**
     * Returns all registered handlers in the ranking order.
     */
    fun getAllKeyEncodingHandlers(): List<KeyEncodingHandler>
}