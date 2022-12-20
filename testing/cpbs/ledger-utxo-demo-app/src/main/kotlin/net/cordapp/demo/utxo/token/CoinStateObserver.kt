package net.cordapp.demo.utxo.token

import net.corda.v5.base.util.loggerFor
import net.corda.v5.ledger.utxo.observer.UtxoLedgerTokenStateObserver
import net.corda.v5.ledger.utxo.observer.UtxoToken
import net.corda.v5.ledger.utxo.observer.UtxoTokenFilterFields
import net.corda.v5.ledger.utxo.observer.UtxoTokenPoolKey

class CoinStateObserver : UtxoLedgerTokenStateObserver<CoinState> {

    private companion object {
        val log = loggerFor<CoinStateObserver>()
    }

    override val stateType = CoinState::class.java

    override fun onCommit(state: CoinState): UtxoToken {
        log.info("Coin observer received state '${state}'")
        return UtxoToken(
            poolKey = UtxoTokenPoolKey(
                tokenType = CoinState.tokenType,
                issuerHash = state.issuer,
                symbol = state.currency
            ),
            state.value,
            filterFields = UtxoTokenFilterFields(state.tag, state.ownerHash)
        )
    }
}