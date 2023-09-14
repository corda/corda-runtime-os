package net.corda.ledger.persistence.utxo

import net.corda.v5.ledger.utxo.ContractState

interface UtxoTokenObserverMap {
    @Suppress("DEPRECATION")
    fun getObserverFor(
        contactStateType: Class<*>
    ): net.corda.v5.ledger.utxo.observer.UtxoLedgerTokenStateObserver<ContractState>?
}

