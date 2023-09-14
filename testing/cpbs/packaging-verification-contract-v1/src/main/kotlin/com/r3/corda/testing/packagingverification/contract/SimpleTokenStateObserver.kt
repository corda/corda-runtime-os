package com.r3.corda.testing.packagingverification.contract

import net.corda.v5.application.crypto.DigestService
import net.corda.v5.ledger.utxo.observer.UtxoToken
import net.corda.v5.ledger.utxo.observer.UtxoTokenFilterFields
import net.corda.v5.ledger.utxo.observer.UtxoTokenPoolKey

@Suppress("DEPRECATION")
class SimpleTokenStateObserver : net.corda.v5.ledger.utxo.observer.UtxoLedgerTokenStateObserver<SimpleState> {
    override fun getStateType() = SimpleState::class.java

    override fun onCommit(state: SimpleState, digestService: DigestService) = UtxoToken(
        UtxoTokenPoolKey(STATE_NAME, state.issuer.toSecureHash(digestService), STATE_SYMBOL),
        state.value.toBigDecimal(),
        UtxoTokenFilterFields()
    )
}
