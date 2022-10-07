package net.corda.v5.cipher.suite

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.cipher.suite.providers.digest.DigestHandler
import net.corda.v5.cipher.suite.providers.encoding.KeyEncodingHandler
import net.corda.v5.cipher.suite.providers.verification.VerifySignatureHandler
import net.corda.v5.cipher.suite.scheme.KeyScheme
import net.corda.v5.crypto.extensions.DigestAlgorithmFactory
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import java.security.PublicKey

@DoNotImplement
interface CipherSuiteBase {

    /**
     * The digests can be extended by the CPI developers as well as by extending the cipher suite.
     */
    fun register(algorithmName: String, digest: DigestHandler)

    fun findKeyScheme(codeName: String): KeyScheme?

    fun findKeyScheme(publicKey: PublicKey): KeyScheme?

    fun findKeyScheme(algorithm: AlgorithmIdentifier): KeyScheme?

    fun findDigestHandler(algorithmName: String): DigestHandler?

    fun findVerifySignatureHandler(schemeCodeName: String): VerifySignatureHandler?

    fun findKeyEncodingHandler(schemeCodeName: String): KeyEncodingHandler?

    /**
     * Returns all registered handlers in the ranking order without duplication.
     */
    fun getAllKeyEncodingHandlers(): List<KeyEncodingHandler>
}