package com.r3.corda.demo.utxo.contract

import net.corda.v5.application.crypto.DigestService
import net.corda.v5.ledger.utxo.observer.UtxoLedgerTokenStateObserver
import net.corda.v5.ledger.utxo.observer.UtxoToken
import net.corda.v5.ledger.utxo.observer.UtxoTokenFilterFields
import net.corda.v5.ledger.utxo.observer.UtxoTokenPoolKey
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class CoinStateObserver : UtxoLedgerTokenStateObserver<CoinState> {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun onCommit(state: CoinState, digestService: DigestService): UtxoToken {
        log.info("Coin observer received state '${state}'")
        return UtxoToken(
            UtxoTokenPoolKey(
                CoinState.tokenType,
                state.issuer,
                state.currency
            ),
            state.value,
            UtxoTokenFilterFields(state.tag, state.ownerHash)
        )
    }

    override fun getStateType(): Class<CoinState> {
        return CoinState::class.java
    }
}