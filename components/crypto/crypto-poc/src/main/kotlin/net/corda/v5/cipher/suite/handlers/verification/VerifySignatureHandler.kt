package net.corda.v5.cipher.suite.handlers.verification

import net.corda.v5.cipher.suite.KeyScheme
import net.corda.v5.crypto.SignatureSpec
import java.security.PublicKey

interface VerifySignatureHandler {
    val rank: Int

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
        scheme: KeyScheme,
        publicKey: PublicKey,
        signatureSpec: SignatureSpec,
        signatureData: ByteArray,
        clearData: ByteArray,
        metadata: ByteArray
    ): Boolean
}