package net.corda.v5.cipher.suite

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.cipher.suite.providers.encoding.KeyEncodingHandler
import net.corda.v5.cipher.suite.providers.verification.VerifySignatureHandler

@DoNotImplement
interface CipherSuite : CipherSuiteBase {
    fun register(
        keyScheme: KeySchemeInfo,
        encodingHandler: KeyEncodingHandler?,
        verifyHandler: VerifySignatureHandler?
    )
}

