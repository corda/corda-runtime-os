package net.corda.ledger.utxo.flow.impl.transaction.filtered

import net.corda.ledger.utxo.data.transaction.UtxoOutputInfoComponent
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredData
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransaction

@CordaSerializable
interface UtxoFilteredTransactionInternal : UtxoFilteredTransaction {

    fun getOutputStates(): UtxoFilteredData<ContractState>

    fun getOutputStateInfos(): UtxoFilteredData<UtxoOutputInfoComponent>
}