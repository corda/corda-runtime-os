package com.example.contract

import java.security.PublicKey
import java.util.Collections.singletonList
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.utxo.ContractState

@CordaSerializable
data class ExampleState(val owner: PublicKey, val amount: Long, val tag: String) : ContractState {
    override fun getParticipants(): List<PublicKey> {
        return singletonList(owner)
    }
}
