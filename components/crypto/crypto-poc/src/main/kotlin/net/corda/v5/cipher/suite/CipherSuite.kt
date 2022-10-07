package net.corda.v5.cipher.suite

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.cipher.suite.handlers.encoding.KeyEncodingHandler
import net.corda.v5.cipher.suite.handlers.verification.VerifySignatureHandler

@DoNotImplement
interface CipherSuite : CipherSuiteBase {
    fun register(
        keyScheme: KeySchemeInfo,
        encodingHandler: KeyEncodingHandler?,
        verifyHandler: VerifySignatureHandler?
    )
}

