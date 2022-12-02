package net.corda.simulator.runtime.ledger

import net.corda.simulator.SimulatorConfiguration
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder
import java.security.PublicKey

class ConsensualTransactionBuilderBase(
    override val states: List<ConsensualState>,
    private val signingService: SigningService,
    private val memberLookup: MemberLookup,
    private val configuration: SimulatorConfiguration
) : ConsensualTransactionBuilder {

    override fun withStates(vararg states: ConsensualState): ConsensualTransactionBuilder {
        return ConsensualTransactionBuilderBase(this.states.plus(states), signingService, memberLookup, configuration)
    }

    override fun toSignedTransaction(): ConsensualSignedTransaction {
        TODO("Not yet implemented")
    }

    @Deprecated("Will be replaced by parameterless version")
    override fun toSignedTransaction(signatory: PublicKey): ConsensualSignedTransaction {
        val unsignedTx = ConsensualSignedTransactionBase(
            listOf(),
            ConsensualStateLedgerInfo(
                states,
                configuration.clock.instant()
            ),
            signingService,
            configuration
        )
        return unsignedTx.addSignature(signatory)
    }
}