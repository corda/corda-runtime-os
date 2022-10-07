package net.corda.v5.cipher.suite

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.cipher.suite.handlers.digest.DigestHandler
import net.corda.v5.cipher.suite.handlers.encoding.KeyEncodingHandler
import net.corda.v5.cipher.suite.handlers.verification.VerifySignatureHandler
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import java.security.PublicKey
import java.security.SecureRandom

@DoNotImplement
interface CipherSuiteBase : SecureRandomProvider {

    fun register(secureRandom: SecureRandom)

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