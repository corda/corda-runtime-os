package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

/**
 * Centralized place for transactions verifications.
 * They are supposed to be called
 *  - at the transaction's creation time from [UtxoTransactionBuilder] or [UtxoSignedTransactionFactory]
 *  - or later on the receiving side of the Finality flow. ([UtxoReceiveFinalityFlow])
 *
 */

class UtxoTransactionVerification {
    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun verifyMetadata(metadata: TransactionMetadata) {
            // TODO(CORE-7116 more verifications)
            // TODO(CORE-7116 ? metadata verifications: nulls, order of CPKs, at least one CPK?)) Maybe from json schema?
        }

        fun verifyNotary(notary: Party?){
            // Notary is not null
            checkNotNull(notary) { "Notary cannot be empty." }
        }

        fun verifyStructures(
            timeWindow: TimeWindow?,
            inputStateRefs: List<StateRef>,
            outputStates: List<ContractState>
        ) {

            // TODO Input notaries same (and later or rotated) as notary

            // timeWindow is not null
            checkNotNull(timeWindow)

            // At least one input, or one output
            require(inputStateRefs.isNotEmpty() || outputStates.isNotEmpty()) {
                "At least one input or output state is required"
            }

            // TODO At least one required signer

            // TODO At least one command

            // TODO probably some more stuff we have to go look at C4 to remember
        }

        /**
         * WIP CORE-5982
         */
        fun verifyLedgerTransaction(tx: UtxoLedgerTransaction) {
            verifyStructures(tx.timeWindow, tx.inputStateRefs, tx.outputContractStates)
            verifyContractStates(tx)
        }

        @Suppress("UNUSED_PARAMETER")
        fun verifyContractStates(tx: UtxoLedgerTransaction) {
            // TODO
        }

    }
}