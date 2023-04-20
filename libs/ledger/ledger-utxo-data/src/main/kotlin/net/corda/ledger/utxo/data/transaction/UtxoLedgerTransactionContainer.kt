package net.corda.ledger.utxo.data.transaction

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
data class UtxoLedgerTransactionContainer(
    val wireTransaction: WireTransaction,
    val inputStateAndRefs: List<UtxoTransactionOutputDto>,
    val referenceStateAndRefs: List<UtxoTransactionOutputDto>
)