package net.corda.simulator.runtime.ledger

import net.corda.simulator.SimulatorConfiguration
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder

fun interface ConsensualTransactionBuilderFactory {
    fun createConsensualTxBuilder(
        signingService: SigningService,
        memberLookup: MemberLookup,
        configuration: SimulatorConfiguration
    ) : ConsensualTransactionBuilder
}

fun consensualTransactionBuilderFactoryBase() : ConsensualTransactionBuilderFactory =
    ConsensualTransactionBuilderFactory { ss, ml, c ->
        ConsensualTransactionBuilderBase(listOf(), ss, ml, c)
}
