package net.corda.v5.cipher.suite

import net.corda.v5.cipher.suite.providers.encoding.KeyEncodingHandler
import net.corda.v5.cipher.suite.providers.generation.GenerateKeyHandler
import net.corda.v5.cipher.suite.providers.signing.SignDataHandler
import net.corda.v5.cipher.suite.providers.verification.VerifySignatureHandler
import net.corda.v5.cipher.suite.scheme.KeyScheme
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.extensions.DigestAlgorithmFactory

interface CryptoWorkerCipherSuite : CipherSuiteBase {
    fun register(
        keyScheme: KeyScheme,
        signatureSpecs: List<SignatureSpec>,
        encodingHandler: KeyEncodingHandler?,
        verifyHandler: VerifySignatureHandler?,
        generateHandler: GenerateKeyHandler?,
        signHandler: SignDataHandler?
    )

    /**
     * The digests can be extended by the CPI developers as well as by extending the cipher suite.
     */
    fun register(digests: List<DigestAlgorithmFactory>)

    fun findGenerateKeyHandler(schemeCodeName: String): GenerateKeyHandler?

    fun findSignDataHandler(schemeCodeName: String): SignDataHandler?
}