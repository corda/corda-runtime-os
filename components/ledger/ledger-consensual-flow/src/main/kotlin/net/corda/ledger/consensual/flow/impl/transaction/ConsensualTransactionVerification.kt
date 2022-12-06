package net.corda.ledger.consensual.flow.impl.transaction

import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction

/**
 * Centralized place for transactions verifications.
 * They are supposed to be called
 *  - at the transaction's creation time from [ConsensualTransactionBuilder] or [ConsensualSignedTransactionFactory]
 *  - or later on the receiving side of the Finality flow. ([ConsensualReceiveFinalityFlow])
 *
 */

class ConsensualTransactionVerification {
    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun verifyMetadata(metadata: TransactionMetadata) {
            // TODO(CORE-5982 more verifications)
            // TODO(CORE-5982 ? metadata verifications: nulls, order of CPKs, at least one CPK?)) Maybe from json schema?
        }

        fun verifyStatesStructure(states: List<ConsensualState>) {
            require(states.isNotEmpty()) { "At least one consensual state is required" }
            require(states.all { it.participants.isNotEmpty() }) { "All consensual states must have participants" }
        }

        /**
         * WIP CORE-5982
         */
        fun verifyLedgerTransaction(tx: ConsensualLedgerTransaction) {
            verifyStatesStructure(tx.states)
            verifySignatoriesConsistent(tx)
            verifyStatesAgaintsTransaction(tx)
        }

        fun verifyStatesAgaintsTransaction(tx: ConsensualLedgerTransaction) {
            tx.states.map { it.verify(tx) }
        }

        fun verifySignatoriesConsistent(tx: ConsensualLedgerTransaction) {
            val requiredSignatoriesFromStates = tx.states
                .flatMap { it.participants }.toSet()
            require(tx.requiredSignatories == requiredSignatoriesFromStates) {
                "Deserialized required signatories from WireTx do not match with the ones derived from the states."
            }
        }

    }
}