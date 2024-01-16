package net.corda.ledger.utxo.flow.impl.transaction.filtered

import net.corda.v5.crypto.merkle.MerkleProof
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransaction

/**
 * This interface adds extra functions to the [UtxoFilteredTransaction] API that should only be visible internally.
 */
interface UtxoFilteredTransactionInternal : UtxoFilteredTransaction {

    /**
     * Returns a [MerkleProof] for the given component group index if exists and returns null if not.
     *
     * @param componentGroupIndex The component group index the merkle proof belongs to
     */
    fun getComponentGroupMerkleProof(componentGroupIndex: Int): MerkleProof?
}
