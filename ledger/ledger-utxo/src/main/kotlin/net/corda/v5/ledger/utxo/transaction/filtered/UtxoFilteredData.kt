package net.corda.v5.ledger.utxo.transaction.filtered

import net.corda.v5.base.annotations.DoNotImplement

/**
 * A container for retrieving information from a [UtxoFilteredTransaction].
 *
 * The underlying data in the original transaction is a component group, i.e. a list of entries in a Merkle tree
 * structure (this is what allows us to filter out bits and still calculate the id). The component group can either
 * be
 * * Completely filtered out - we do not get any inforamtion about this data set
 * * A size-only merkle proof - in this case, we can only retrieve the size of the original list
 * * An audit proof - in this case, we get access to some or all of the entries of the original list.
 */
@DoNotImplement
interface UtxoFilteredData<T> {

    /**
     * Marker interface for a completely filtered out component group. No further information is available
     */
    @DoNotImplement
    interface Removed<T> : UtxoFilteredData<T>

    /**
     * Interface for a size only proof. This will only tell us how many entries there were originally, but not their
     * content.
     */
    @DoNotImplement
    interface SizeOnly<T> : UtxoFilteredData<T> {
        /**
         * @property size The size of the component group in the original transaction
         */
        val size: Int
    }

    /**
     * Interface for an audit proof.
     *
     * This allows us to retrieve some or all of the original entries. The size entry
     * will be the size of the component group in the original transaction. The values will be a list of all entries
     * that we get access to - its size can be less than the size value. The values are given as a map of
     * original index to value.
     */
    @DoNotImplement
    interface Audit<T> : UtxoFilteredData<T> {

        /**
         * @property size The size of the component group in the original transaction
         */
        val size: Int

        /**
         * @property values A map of revealed entries with mapped by their index in the unfiltered transaction
         */
        val values: Map<Int, T>
    }
}