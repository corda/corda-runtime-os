package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.TransactionNoAvailableKeysException
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import java.security.PublicKey

interface UtxoTransactionBuilderInternal : UtxoTransactionBuilder {
    val timeWindow: TimeWindow?
    val attachments: List<SecureHash>
    val commands: List<Command>
    val signatories: List<PublicKey>
    val inputStateRefs: List<StateRef>
    val referenceStateRefs: List<StateRef>
    val outputStates: List<ContractStateAndEncumbranceTag>

    /**
     * Verifies the content of the [UtxoTransactionBuilder] and
     * signs the transaction with any required signatories that belong to the current node.
     *
     * Calling this function once consumes the [UtxoTransactionBuilder], so it cannot be used again.
     * Therefore, if you want to build two transactions you need two builders.
     *
     * @return Returns a [UtxoSignedTransactionInternal] with signatures for any required signatories that belong to the current node.
     *
     * @throws IllegalStateException when called a second time on the same object to prevent
     *      unintentional duplicate transactions.
     * @throws TransactionNoAvailableKeysException if none of the required keys are available to sign the transaction.
     */
    @Suspendable
    fun toSignedTransaction(): UtxoSignedTransactionInternal
}