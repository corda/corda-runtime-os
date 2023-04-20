package net.corda.v5.ledger.utxo;

import net.corda.v5.base.annotations.DoNotImplement;
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction;
import org.jetbrains.annotations.NotNull;

/**
 * Defines the result of transaction finalization
 *
 */
@DoNotImplement
public interface FinalizationResult {
    /**
     * Gets the finalized transaction.
     *
     * @return An instance of {@link UtxoSignedTransaction}.
     */
    @NotNull
    UtxoSignedTransaction getTransaction();
}
