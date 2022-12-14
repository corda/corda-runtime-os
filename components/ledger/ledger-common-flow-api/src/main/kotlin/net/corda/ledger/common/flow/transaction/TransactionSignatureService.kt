package net.corda.ledger.common.flow.transaction

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import java.security.PublicKey

interface TransactionSignatureService {
    @Suspendable
    fun sign(transactionId: SecureHash, publicKey: PublicKey): DigitalSignatureAndMetadata

    /**
     * The underlying verification service signals the verification failures with different exceptions.
     * [DigitalSignatureVerificationService]
     */
    fun verifySignature(transactionId: SecureHash, signatureWithMetadata: DigitalSignatureAndMetadata)

    /**
     * The underlying verification service signals the verification failures with different exceptions.
     * [DigitalSignatureVerificationService]
     * Currently, Notaries returns a temporary structure what we decode here.
     * TODO Fix it with BatchSignatures
     */
    fun verifyNotarySignature(transactionId: SecureHash, signatureWithMetadata: DigitalSignatureAndMetadata)
}