package net.corda.v5.ledger.utxo.transaction.filtered;

import net.corda.v5.base.annotations.DoNotImplement;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Defines a container for retrieving information from a {@link UtxoFilteredTransaction}.
 * <p>
 * The underlying data in the original transaction is a component group, i.e. a list of entries in a Merkle tree
 * structure. This is what allows us to filter the transaction and still calculate a consistent transaction ID.
 * <p>
 * The component group can be either:
 * - Completely filtered out, where we do not get any information about this data.
 * - A size-only merkle proof, where we can only retrieve the size of the original list.
 * - An audit proof, where we get access to some or all of the entries of the original list.
 *
 * @param <T> The underlying type of the filtered data.
 */
@DoNotImplement
public interface UtxoFilteredData<T> {

    /**
     * Defines a completely filtered out component group, where we do not get any information about this data.
     *
     * @param <T> The underlying type of the filtered data.
     */
    @DoNotImplement
    interface Removed<T> extends UtxoFilteredData<T> {
    }

    /**
     * Defines a size-only proof, where we can only retrieve the size of the original list.
     *
     * @param <T> The underlying type of the filtered data.
     */
    @DoNotImplement
    interface SizeOnly<T> extends UtxoFilteredData<T> {

        /**
         * Gets the size of the component group in the original transaction.
         *
         * @return Returns the size of the component group in the original transaction.
         */
        int getSize();
    }

    /**
     * Defines an audit proof, where we get access to some or all of the entries of the original list.
     * <p>
     * This allows retrieval of some or all of the original entries. The size entry will be the size of the component
     * group in the original transaction. The values will be a list of all entries that we get access to. Its size
     * can be less than the size value. The values are given as a map of original index to value.
     *
     * @param <T> The underlying type of the filtered data.
     */
    @DoNotImplement
    interface Audit<T> extends UtxoFilteredData<T> {

        /**
         * Gets the size of the component group in the original transaction.
         *
         * @return Returns the size of the component group in the original transaction.
         */
        int getSize();

        /**
         * Gets the map of revealed entries with mapped by their index in the unfiltered transaction.
         *
         * @return Returns the map of revealed entries with mapped by their index in the unfiltered transaction.
         */
        @NotNull
        Map<Integer, T> getValues();
    }
}
