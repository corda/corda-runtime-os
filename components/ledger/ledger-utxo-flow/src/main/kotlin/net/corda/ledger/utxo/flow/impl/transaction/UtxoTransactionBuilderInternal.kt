package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import java.security.PublicKey

interface UtxoTransactionBuilderInternal : UtxoTransactionBuilder, UtxoTransactionBuilderData {
    /**
     * Returns another transaction builder with the same content.
     *
     * @return A copy of the current transaction builder.
     */
    fun copy(): UtxoTransactionBuilderContainer

    /**
     * Appends transaction builder components to a transaction builder.
     * Also, notary and time window of the original takes precedence.
     * Those will not be overwritten regardless of there are new values.
     * It de-duplicates the
     *  - attachments
     *  - signatories
     *  - inputStateRefs
     *  - referenceStateRefs
     * But keeps potential duplications in user-defined types. (commands and output states)
     */
    fun append(other: UtxoTransactionBuilderData): UtxoTransactionBuilderInternal

    /**
     * The key of the selected notary
     */
    var notaryKey: PublicKey?
}