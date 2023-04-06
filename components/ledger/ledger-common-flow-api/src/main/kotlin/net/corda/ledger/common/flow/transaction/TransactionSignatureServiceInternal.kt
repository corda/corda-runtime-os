package net.corda.ledger.common.flow.transaction

import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.TransactionSignatureService
import java.security.PublicKey

interface TransactionSignatureServiceInternal : TransactionSignatureService {

    fun getIdOfPublicKey(
        publicKey: PublicKey,
        digestAlgorithmName: String
    ): SecureHash
}