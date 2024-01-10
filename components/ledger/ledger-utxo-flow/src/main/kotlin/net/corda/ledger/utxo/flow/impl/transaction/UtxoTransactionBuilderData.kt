package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import java.security.PublicKey

interface UtxoTransactionBuilderData {
    val timeWindow: TimeWindow?
    val commands: List<Command>
    val signatories: List<PublicKey>
    val inputStateRefs: List<StateRef>
    val referenceStateRefs: List<StateRef>
    val outputStates: List<ContractStateAndEncumbranceTag>

    val dependencies: Set<SecureHash>
        get() = this
            .let { it.inputStateRefs.asSequence() + it.referenceStateRefs.asSequence() }
            .map { it.transactionId }
            .toSet()

    fun getNotaryName(): MemberX500Name?
}
