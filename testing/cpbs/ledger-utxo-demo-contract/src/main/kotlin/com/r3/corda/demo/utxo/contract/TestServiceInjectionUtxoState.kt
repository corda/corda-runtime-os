package com.r3.corda.demo.utxo.contract

import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.ContractState
import java.security.PublicKey

@BelongsToContract(TestServiceInjectionContract::class)
class TestServiceInjectionUtxoState(
    val identifier: Int,
    val testField: String,
    private val participants: List<PublicKey>,
    val participantNames: List<String>
) : ContractState {
    constructor(
        testField: String,
        participants: List<PublicKey>,
        participantNames: List<String>
    ) : this(2, testField, participants, participantNames)

    override fun getParticipants(): List<PublicKey> {
        return participants
    }

    override fun toString(): String {
        return "testField: '$testField'; " +
                "participants: $participants ;" +
                "participantNames: $participantNames ;"
    }
}