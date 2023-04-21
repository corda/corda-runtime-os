package net.corda.v5.ledger.utxo.transaction.filtered;

import net.corda.v5.base.exceptions.CordaRuntimeException;
import org.jetbrains.annotations.NotNull;

/**
 * Represents the exception that is thrown when filtered data is inconsistent with the original transaction.
 */
public final class FilteredDataInconsistencyException extends CordaRuntimeException {

    /**
     * Creates a new instance of the {@link FilteredDataInconsistencyException} class.
     *
     * @param message The details of the current exception to throw.
     */
    public FilteredDataInconsistencyException(@NotNull String message) {
        super(message);
    }
}
