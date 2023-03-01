package net.corda.v5.ledger.utxo.transaction.filtered;

import net.corda.v5.base.annotations.DoNotImplement;
import net.corda.v5.base.exceptions.CordaRuntimeException;
import net.corda.v5.crypto.SecureHash;
import net.corda.v5.ledger.common.Party;
import net.corda.v5.ledger.common.transaction.TransactionMetadata;
import net.corda.v5.ledger.utxo.Command;
import net.corda.v5.ledger.utxo.StateAndRef;
import net.corda.v5.ledger.utxo.StateRef;
import net.corda.v5.ledger.utxo.TimeWindow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.PublicKey;

/**
 * Defines a filtered UTXO transaction.
 * <p>
 * This wraps a signed transaction that has been filtered using merkle proofs. This means that we can still calculate
 * and verify the transaction ID as a Merkle hash, but do not have access to all data in the original transaction.
 * <p>
 * For the list based data properties, there are three possibilities:
 * - The whole entry is filtered out - no further information about this data is available.
 * This will be signified by returning an object implementing {@link UtxoFilteredData.Removed}.
 * - Only the number of original entries is revealed, but not the actual data. In this case,
 * an object implementing {@link UtxoFilteredData.SizeOnly} is returned.
 * - Some or all of the original data is revealed. In this case, an object implementing
 * {@link UtxoFilteredData.Audit} is returned.
 * <p>
 * There are a few special cases:
 * - {@link #getId()} and {@link #getMetadata()} cannot be filtered and are always returned
 * - {@link #getNotary()} and {@link #getTimeWindow()} are always unique - they are either revealed,
 * or the filtered transaction will return null when accessing them.
 */
@DoNotImplement
public interface UtxoFilteredTransaction {

    /**
     * Gets the ID of the current transaction.
     *
     * @return Returns the ID of the current transaction.
     */
    @NotNull
    SecureHash getId();

    /**
     * Gets the metadata for the current transaction.
     *
     * @return Returns the metadata for the current transaction.
     */
    @NotNull
    TransactionMetadata getMetadata();

    /**
     * Gets the validity time window for finalizing/notarising the current transaction, or null if filtered.
     *
     * @return Returns the validity time window for finalizing/notarising the current transaction, or null if filtered.
     */
    @Nullable
    TimeWindow getTimeWindow();

    /**
     * Gets the notary for the current transaction, or null if filtered.
     *
     * @return Returns the notary for the current transaction, or null if filtered.
     */
    @Nullable
    Party getNotary();

    /**
     * Gets a potentially filtered list of required signatories for the current transaction.
     *
     * @return Returns a potentially filtered list of required signatories for the current transaction.
     */
    @NotNull
    UtxoFilteredData<PublicKey> getSignatories();

    /**
     * Gets a potentially filtered list of commands for the current transaction.
     *
     * @return Returns a potentially filtered list of commands for the current transaction.
     */
    @NotNull
    UtxoFilteredData<Command> getCommands();

    /**
     * Gets a potentially filtered list of input state refs for the current transaction.
     *
     * @return Returns a potentially filtered list of input state refs for the current transaction.
     */
    @NotNull
    UtxoFilteredData<StateRef> getInputStateRefs();

    /**
     * Gets a potentially filtered list of reference input state refs for the current transaction.
     *
     * @return Returns a potentially filtered list of reference input state refs for the current transaction.
     */
    @NotNull
    UtxoFilteredData<StateRef> getReferenceStateRefs();

    /**
     * Gets a potentially filtered list of output state refs for the current transaction.
     *
     * @return Returns a potentially filtered list of output state refs for the current transaction.
     */
    @NotNull
    UtxoFilteredData<StateAndRef<?>> getOutputStateAndRefs();

    /**
     * Verifies the current {@link UtxoFilteredTransaction}.
     *
     * @throws CordaRuntimeException if the current {@link UtxoFilteredTransaction} fails to verify correctly.
     */
    void verify();
}
