package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.ledger.utxo.data.state.EncumbranceGroupImpl
import net.corda.ledger.utxo.data.state.TransactionStateImpl
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.TransactionState

@CordaSerializable
data class ContractStateAndEncumbranceTag(val contractState: ContractState, val encumbranceTag: String?) {

    fun toTransactionState(notary: Party, encumbranceGroupSize: Int?): TransactionState<*> {
        return TransactionStateImpl(contractState, notary, encumbranceTag?.let{
            require(encumbranceGroupSize != null)
            EncumbranceGroupImpl(encumbranceGroupSize, it)
        })
    }
}
