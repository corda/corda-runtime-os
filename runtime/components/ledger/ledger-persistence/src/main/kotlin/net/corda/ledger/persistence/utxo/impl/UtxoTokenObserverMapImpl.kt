package net.corda.ledger.persistence.utxo.impl

import net.corda.ledger.persistence.utxo.UtxoTokenObserverMap
import net.corda.persistence.common.getTokenStateObservers
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.observer.UtxoLedgerTokenStateObserver


class UtxoTokenObserverMapImpl(private val sandboxGroupContext: SandboxGroupContext) :
    UtxoTokenObserverMap {

    override fun getObserversFor(contactStateType: Class<*>): List<UtxoLedgerTokenStateObserver<ContractState>> {
        return sandboxGroupContext.getTokenStateObservers()[contactStateType] ?: emptyList()
    }
}
