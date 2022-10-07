package net.corda.v5.cipher.suite

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.cipher.suite.handlers.encoding.KeyEncodingHandler
import net.corda.v5.cipher.suite.handlers.generation.GenerateKeyHandler
import net.corda.v5.cipher.suite.handlers.signing.SignDataHandler
import net.corda.v5.cipher.suite.handlers.verification.VerifySignatureHandler

@DoNotImplement
interface CryptoWorkerCipherSuite : CipherSuiteBase {
    fun register(
        keyScheme: KeySchemeInfo,
        encodingHandler: KeyEncodingHandler?,
        verifyHandler: VerifySignatureHandler?,
        generateHandler: GenerateKeyHandler?,
        signHandler: SignDataHandler?
    )

    fun findGenerateKeyHandler(schemeCodeName: String): GenerateKeyHandler?

    fun findSignDataHandler(schemeCodeName: String): SignDataHandler?
}