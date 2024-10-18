package net.corda.ledger.utxo.data.transaction

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.membership.GroupParameters

@CordaSerializable
data class UtxoLedgerTransactionContainer(
    val wireTransaction: WireTransaction,
    val inputStateAndRefs: List<StateAndRef<*>>,
    val referenceStateAndRefs: List<StateAndRef<*>>,
    val groupParameters: GroupParameters?
)
