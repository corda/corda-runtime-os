package net.corda.crypto.service.ciphersuite2.publicapi.providers

import net.corda.v5.cipher.suite.schemes.KeyScheme
import net.corda.v5.crypto.SignatureSpec
import java.security.PublicKey

interface VerifySignatureHandlerProvider {
    val supportedKeyScheme: KeyScheme
    val supportedSignatureSpec: List<SignatureSpec>
    fun getInstance(): VerifySignatureHandler
}

interface VerifySignatureHandler {
    /**
     * Verifies a digital signature by using [signatureSpec].
     * It returns true if it succeeds and false if not. Normally you should use the function which throws an exception,
     * as it avoids the risk of failing to test the result.
     *
     * @param publicKey the signer's [PublicKey].
     * @param signatureData the signatureData on a message.
     * @param signatureSpec the signature spec.
     * @param clearData the clear data/message that was signed
     *
     * @return true if verification passes or false if verification fails.
     *
     * @throws IllegalArgumentException if any of the clear or signature data is empty, key is invalid,
     * the signature scheme is not supported or in general arguments are wrong
     */
    fun isValid(
        publicKey: PublicKey,
        signatureSpec: SignatureSpec,
        signatureData: ByteArray,
        clearData: ByteArray
    ): Boolean
}