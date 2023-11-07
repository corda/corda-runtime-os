package com.r3.corda.testing.packagingverification.contract

import net.corda.v5.ledger.utxo.observer.TokenStateObserverContext
import net.corda.v5.ledger.utxo.observer.UtxoToken
import net.corda.v5.ledger.utxo.observer.UtxoTokenFilterFields
import net.corda.v5.ledger.utxo.observer.UtxoTokenPoolKey
import net.corda.v5.ledger.utxo.observer.UtxoTokenTransactionStateObserver

class SimpleTokenStateObserver : UtxoTokenTransactionStateObserver<SimpleState> {
    override fun getStateType() = SimpleState::class.java

    override fun onCommit(context: TokenStateObserverContext<SimpleState>) = UtxoToken(
        UtxoTokenPoolKey(STATE_NAME, context.stateAndRef.state.contractState.issuer.toSecureHash(context.digestService), STATE_SYMBOL),
        context.stateAndRef.state.contractState.value.toBigDecimal(),
        UtxoTokenFilterFields()
    )
}
