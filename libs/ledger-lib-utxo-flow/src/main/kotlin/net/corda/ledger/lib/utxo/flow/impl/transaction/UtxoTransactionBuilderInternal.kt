package net.corda.ledger.lib.utxo.flow.impl.transaction

import net.corda.ledger.common.data.transaction.PrivacySalt
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder

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
     *  - signatories
     *  - inputStateRefs
     *  - referenceStateRefs
     * But keeps potential duplications in user-defined types. (commands and output states)
     */
    fun append(other: UtxoTransactionBuilderData): UtxoTransactionBuilderInternal

    var privacySalt: PrivacySalt?
}

// set privacy salt variable / dedup id on thread local
// create transaction builder
// turn into signed transaction -> this uses the thread local value
// unset thread local
