package net.corda.ledger.persistence.utxo.impl

import net.corda.ledger.persistence.utxo.UtxoTokenObserverMap
import net.corda.persistence.common.getTokenStateObservers
import net.corda.persistence.common.getTokenStateObserversV2
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.observer.UtxoTokenTransactionStateObserver

class UtxoTokenObserverMapImpl(private val sandboxGroupContext: SandboxGroupContext) :
    UtxoTokenObserverMap {

    @Suppress("DEPRECATION")
    override fun getObserverFor(
        contactStateType: Class<*>
    ): net.corda.v5.ledger.utxo.observer.UtxoLedgerTokenStateObserver<ContractState>? {
        return sandboxGroupContext.getTokenStateObservers()[contactStateType]
    }

    override fun getObserverForV2(contactStateType: Class<*>): UtxoTokenTransactionStateObserver<ContractState>? {
        return sandboxGroupContext.getTokenStateObserversV2()[contactStateType]
    }
}
