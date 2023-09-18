package com.r3.corda.testing.packagingverification.contract

import net.corda.v5.application.crypto.DigestService
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.observer.UtxoToken
import net.corda.v5.ledger.utxo.observer.UtxoTokenFilterFields
import net.corda.v5.ledger.utxo.observer.UtxoTokenPoolKey
import net.corda.v5.ledger.utxo.observer.UtxoTokenTransactionStateObserver
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

class SimpleTokenStateObserver : UtxoTokenTransactionStateObserver<SimpleState> {
    override fun getStateType() = SimpleState::class.java

    override fun onCommit(
        stateAndRef: StateAndRef<SimpleState>,
        transaction: UtxoLedgerTransaction,
        digestService: DigestService
    ) = UtxoToken(
        UtxoTokenPoolKey(STATE_NAME, stateAndRef.state.contractState.issuer.toSecureHash(digestService), STATE_SYMBOL),
        stateAndRef.state.contractState.value.toBigDecimal(),
        UtxoTokenFilterFields()
    )
}
