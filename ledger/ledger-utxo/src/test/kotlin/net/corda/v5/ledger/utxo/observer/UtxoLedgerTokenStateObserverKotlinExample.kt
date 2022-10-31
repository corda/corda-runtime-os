@file:Suppress("UNUSED_PARAMETER")

package net.corda.v5.ledger.utxo.observer

import net.corda.v5.ledger.utxo.StateAndRef

/**
 * This tests validates the code example in the KDoc comments will compile
 */
class UtxoLedgerTokenStateObserverKotlinExample : UtxoLedgerTokenStateObserver<ExampleStateK> {

    override val stateType = ExampleStateK::class.java

    override fun onProduced(stateAndRef: StateAndRef<ExampleStateK>): UtxoToken {
        val state = stateAndRef.state.contractState
        return UtxoToken(
            UtxoTokenPoolKey(ExampleStateK::class.java.name, state.issuer, state.currency),
            state.amount,
            UtxoTokenFilterFields()
        )
    }
}
