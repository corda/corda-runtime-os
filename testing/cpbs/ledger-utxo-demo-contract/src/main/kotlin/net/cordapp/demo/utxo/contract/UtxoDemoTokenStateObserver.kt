package net.cordapp.demo.utxo.contract

import net.corda.v5.application.crypto.DigestService
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.observer.UtxoLedgerTokenStateObserver
import net.corda.v5.ledger.utxo.observer.UtxoToken
import net.corda.v5.ledger.utxo.observer.UtxoTokenFilterFields
import net.corda.v5.ledger.utxo.observer.UtxoTokenPoolKey
import java.math.BigDecimal

class UtxoDemoTokenStateObserver : UtxoLedgerTokenStateObserver<TestUtxoState> {

    override fun getStateType(): Class<TestUtxoState> {
        return TestUtxoState::class.java
    }

    override fun onCommit(state: TestUtxoState, digestService: DigestService): UtxoToken {
        return UtxoToken(
            UtxoTokenPoolKey(
                TestUtxoState::class.java.name,
                digestService.parseSecureHash(
                    "SHA-256:EC4F2DBB3B140095550C9AFBBB69B5D6FD9E814B9DA82FAD0B34E9FCBE56F1CB"
                ),
                state.testField
            ),
            BigDecimal.TEN,
            UtxoTokenFilterFields()
        )
    }
}