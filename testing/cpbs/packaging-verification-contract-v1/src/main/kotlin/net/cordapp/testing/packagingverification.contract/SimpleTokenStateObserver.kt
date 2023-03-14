package net.cordapp.testing.packagingverification.contract

import net.corda.v5.ledger.utxo.observer.UtxoLedgerTokenStateObserver
import net.corda.v5.ledger.utxo.observer.UtxoToken
import net.corda.v5.ledger.utxo.observer.UtxoTokenFilterFields
import net.corda.v5.ledger.utxo.observer.UtxoTokenPoolKey

class SimpleTokenStateObserver : UtxoLedgerTokenStateObserver<SimpleState> {
    override fun getStateType() = SimpleState::class.java

    override fun onCommit(state: SimpleState) = UtxoToken(
        UtxoTokenPoolKey(STATE_NAME, ISSUER.toSecureHash(), STATE_SYMBOL),
        state.value.toBigDecimal(),
        UtxoTokenFilterFields()
    )
}
