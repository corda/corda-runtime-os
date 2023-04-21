package net.corda.ledger.persistence.utxo

import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.observer.UtxoLedgerTokenStateObserver

interface UtxoTokenObserverMap {
    fun getObserversFor(contactStateType: Class<*>): List<UtxoLedgerTokenStateObserver<ContractState>>
}

