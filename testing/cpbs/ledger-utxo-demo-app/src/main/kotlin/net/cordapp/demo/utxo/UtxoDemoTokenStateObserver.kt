package net.cordapp.demo.utxo

import net.corda.v5.base.util.loggerFor
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.observer.UtxoLedgerTokenStateObserver
import net.corda.v5.ledger.utxo.observer.UtxoToken
import net.corda.v5.ledger.utxo.observer.UtxoTokenFilterFields
import net.corda.v5.ledger.utxo.observer.UtxoTokenPoolKey
import java.math.BigDecimal

class UtxoDemoTokenStateObserver : UtxoLedgerTokenStateObserver<UtxoDemoFlow.TestUtxoState> {

    private companion object {
        val log = loggerFor<UtxoDemoTokenStateObserver>()
    }

    override val stateType = UtxoDemoFlow.TestUtxoState::class.java

    override fun onCommit(state: UtxoDemoFlow.TestUtxoState): UtxoToken {
        log.info("HERE I AM $state")
        return UtxoToken(
            UtxoTokenPoolKey(
                UtxoDemoFlow.TestUtxoState::class.java.name,
                SecureHash(
                    DigestAlgorithmName.SHA2_256.name,
                    "EC4F2DBB3B140095550C9AFBBB69B5D6FD9E814B9DA82FAD0B34E9FCBE56F1CB".toByteArray()
                ),
                state.testField
            ),
            BigDecimal.TEN,
            UtxoTokenFilterFields()
        )
    }
}