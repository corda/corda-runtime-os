package net.corda.ledger.common.flow.transaction

import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.TransactionSignatureVerificationService
import java.security.PublicKey

interface TransactionSignatureVerificationServiceInternal : TransactionSignatureVerificationService {

    fun getIdOfPublicKey(
        publicKey: PublicKey,
        digestAlgorithmName: String
    ): SecureHash
}
