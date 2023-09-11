@file:Suppress("DEPRECATION")
package net.corda.ledger.persistence.utxo

import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.observer.UtxoLedgerTokenStateObserver
import net.corda.v5.ledger.utxo.observer.UtxoTokenTransactionStateObserver

interface UtxoTokenObserverMap {
    fun getObserverFor(contactStateType: Class<*>): UtxoLedgerTokenStateObserver<ContractState>?
    fun getObserverForV2(contactStateType: Class<*>): UtxoTokenTransactionStateObserver<ContractState>?
}

