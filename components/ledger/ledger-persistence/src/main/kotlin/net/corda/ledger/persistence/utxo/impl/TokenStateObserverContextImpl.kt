package net.corda.ledger.persistence.utxo.impl

import net.corda.v5.application.crypto.DigestService
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.observer.TokenStateObserverContext

class TokenStateObserverContextImpl(private val stateAndRef: StateAndRef<ContractState>, private val digestService: DigestService) :
    TokenStateObserverContext<ContractState> {

    override fun getStateAndRef(): StateAndRef<ContractState> =
        stateAndRef

    override fun getDigestService(): DigestService =
        digestService
}
