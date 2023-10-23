package net.corda.ledger.persistence.utxo

import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.observer.UtxoTokenTransactionStateObserver

interface UtxoTokenObserverMap {
    @Suppress("DEPRECATION")
    fun getObserverFor(contactStateType: Class<*>): net.corda.v5.ledger.utxo.observer.UtxoLedgerTokenStateObserver<ContractState>?
    fun getObserverForV2(contactStateType: Class<*>): UtxoTokenTransactionStateObserver<ContractState>?
}
