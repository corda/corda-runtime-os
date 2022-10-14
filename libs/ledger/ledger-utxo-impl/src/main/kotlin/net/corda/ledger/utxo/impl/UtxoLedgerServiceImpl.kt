package net.corda.ledger.utxo.impl

import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder

class UtxoLedgerServiceImpl : UtxoLedgerService {

    override fun getTransactionBuilder(): UtxoTransactionBuilder {
        TODO("Not yet implemented")
    }

    override fun <T : ContractState> resolve(stateRefs: Iterable<StateRef>): List<StateAndRef<T>> {
        TODO("Not yet implemented")
    }

    override fun <T : ContractState> resolve(stateRef: StateRef): StateAndRef<T> {
        TODO("Not yet implemented")
    }

    override fun <T : ContractState> verify(stateAndRefs: Iterable<StateAndRef<T>>) {
        TODO("Not yet implemented")
    }

    override fun <T : ContractState> verify(stateAndRef: StateAndRef<T>) {
        TODO("Not yet implemented")
    }
}
