package net.corda.ledger.common.testkit

import net.corda.v5.application.crypto.SigningService
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
import java.security.PublicKey

private class MockSigningService :SigningService{
    override fun sign(bytes: ByteArray, publicKey: PublicKey, signatureSpec: SignatureSpec): DigitalSignature.WithKey {
        return DigitalSignature.WithKey(publicKeyExample, "0".toByteArray(), mapOf())
    }
}

fun mockSigningService(): SigningService {
    return MockSigningService()
}