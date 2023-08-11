package com.r3.corda.demo.utxo.contract.notaryverify

import com.r3.corda.demo.utxo.contract.TestUtxoState
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.BelongsToContract
import java.security.PublicKey

@BelongsToContract(NotaryVerifyContract::class)
class NotaryVerifyState(
    val transactionIdToVerify: SecureHash,
    val notarySignature: DigitalSignatureAndMetadata,
    val notaryPublicKey: PublicKey,
    participants: List<PublicKey>,
    participantNames: List<String>
) : TestUtxoState(testField = transactionIdToVerify.toString(), participants, participantNames) {

    override fun toString(): String{
        return "transaction id to verify: ${transactionIdToVerify};" +
                "participants: $participants ;"
    }
}
