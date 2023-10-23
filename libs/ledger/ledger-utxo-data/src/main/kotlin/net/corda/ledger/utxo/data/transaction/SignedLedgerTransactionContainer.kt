package net.corda.ledger.utxo.data.transaction

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.SecureHash

@CordaSerializable
data class SignedLedgerTransactionContainer(
    val wireTransaction: WireTransaction,
    val serializedInputStateAndRefs: List<UtxoVisibleTransactionOutputDto>,
    val serializedReferenceStateAndRefs: List<UtxoVisibleTransactionOutputDto>,
    val signatures: List<DigitalSignatureAndMetadata>
) {
    val id: SecureHash
        get() = wireTransaction.id
}
