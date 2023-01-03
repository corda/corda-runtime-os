package net.corda.v5.ledger.utxo.transaction.filtered

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import java.security.PublicKey

/**
 * A filtered UTXO transaction.
 *
 * This class wraps a signed transaction that has been filtered using merkle proofs. This means
 * that we can still calculate and verify the transaction id as Merkle hash, but do not
 * have access to all data in the original transaction.
 *
 * For the list based data properties, there are three possibilities:
 * - The whole entry is filtered out - no further information about this data is available.
 *   This will be signified by returning an object implementing [UtxoFilteredData.Removed]
 * - Only the number of original entries is revealed, but not the actual data. In this case,
 *   an object implementing [UtxoFilteredData.SizeOnly] is returned
 * - Some or all of the original data is revealed. In this case, an object implementing
 *   [UtxoFilteredData.Audit] is returned.
 *
 *  There are a few special cases:
 *  - [id] and [metadata] cannot be filtered and are always returned
 *  - [notary] and [timeWindow] are always unique - they are either revealed, or the filtered transaction
 *    will return null when accessing them.
 */
@DoNotImplement
interface UtxoFilteredTransaction {
    /**
     * @property id The ID of the transaction.
     */
    val id: SecureHash

    /**
     * @property metadata The metadata for this transaction
     */
    val metadata: TransactionMetadata

    /**
     * @property timeWindow The validity time window for finalizing/notarising this transaction or null if filtered
     */
    val timeWindow: TimeWindow?

    /**
     * @property notary The notary party for this transaction or null if filtered
     */
    val notary: Party?

    /**
     * @property signatories Potentially filtered list of required signers
     */
    val signatories: UtxoFilteredData<PublicKey>

    /**
     * @property inputStateRefs Potentially filtered list of input state refs
     */
    val inputStateRefs: UtxoFilteredData<StateRef>

    /**
     * @property referenceStateRefs Potentially filtered list of reference state refs
     */
    val referenceStateRefs: UtxoFilteredData<StateRef>

    /**
     * @property outputStateAndRefs Potentially filtered list of outputs
     * @throws FilteredDataInconsistencyException If the output states and state type information have been filtered inconsistently
     */
    val outputStateAndRefs: UtxoFilteredData<StateAndRef<*>>

    /**
     * @property commands Potentially filtered list of commands
     */
    val commands: UtxoFilteredData<Command>

    /**
     * Verifies the current [UtxoFilteredTransaction].
     *
     * @throws CordaRuntimeException If the current [UtxoFilteredTransaction] fails to verify correctly.
     */
    fun verify()
}