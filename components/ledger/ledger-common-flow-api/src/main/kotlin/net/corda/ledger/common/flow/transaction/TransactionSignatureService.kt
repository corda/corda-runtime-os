package net.corda.ledger.common.flow.transaction

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import java.security.PublicKey

interface TransactionSignatureService {
    @Suspendable
    fun sign(txId: SecureHash, publicKey: PublicKey): DigitalSignatureAndMetadata

    @Suspendable
    fun verifySignature()
}