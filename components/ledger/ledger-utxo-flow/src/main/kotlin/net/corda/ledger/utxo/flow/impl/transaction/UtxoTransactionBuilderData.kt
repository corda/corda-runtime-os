package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import java.security.PublicKey

interface UtxoTransactionBuilderData {
    val timeWindow: TimeWindow?
    val attachments: List<SecureHash>
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

    fun getNotary(): Party?

    /**
     * Calculates what got added to a transaction builder comparing to another.
     * Notary and TimeWindow changes are not considered if the original had them set already.
     * This gives precedence to those original values.
     */
    operator fun minus(orig: UtxoTransactionBuilderData): UtxoTransactionBuilderContainer =
        UtxoTransactionBuilderContainer(
            if (orig.getNotary() == null) getNotary() else null,
            if (orig.timeWindow == null) timeWindow else null,
            attachments - orig.attachments.toSet(),
            commands - orig.commands.toSet(),
            signatories - orig.signatories.toSet(),
            inputStateRefs - orig.inputStateRefs.toSet(),
            referenceStateRefs - orig.referenceStateRefs.toSet(),
            outputStates - orig.outputStates.toSet()
        )
}