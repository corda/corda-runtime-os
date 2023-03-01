package net.corda.v5.ledger.utxo.transaction;

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata;
import net.corda.v5.base.annotations.DoNotImplement;
import net.corda.v5.base.annotations.Suspendable;
import net.corda.v5.ledger.common.Party;
import net.corda.v5.ledger.common.transaction.TransactionWithMetadata;
import net.corda.v5.ledger.utxo.Command;
import net.corda.v5.ledger.utxo.StateAndRef;
import net.corda.v5.ledger.utxo.StateRef;
import net.corda.v5.ledger.utxo.TimeWindow;
import org.jetbrains.annotations.NotNull;

import java.security.PublicKey;
import java.util.List;

/**
 * Defines a signed UTXO transaction.
 * <p>
 * Comparing with {@link UtxoLedgerTransaction}:
 * <ul>
 *     <li>It does not have access to the deserialized details.</li>
 *     <li>It has direct access to the signatures.</li>
 *     <li>It does not require a serializer.</li>
 * </ul>
 * <p>
 * {@link UtxoSignedTransaction} wraps the wire representation of the transaction. It contains one or more signatures,
 * each one for a public key (including composite keys) that is mentioned inside a transaction state.
 * <p>
 * {@link UtxoSignedTransaction} is frequently passed around the network and stored. The identity of a transaction is
 * the hash of Merkle root of the wrapped wire representation, therefore if you are storing data keyed by wire
 * representations hash be aware that multiple different {@link UtxoSignedTransaction}s may map to the same key, and
 * they could be different in important ways, like validity!
 * <p>
 * The signatures on a {@link UtxoSignedTransaction} might be invalid or missing: the type does not imply validity.
 * A transaction ID should be the hash of the wrapped wire representation's Merkle tree root, therefore adding or
 * removing a signature does not change it.
 */
@DoNotImplement
public interface UtxoSignedTransaction extends TransactionWithMetadata {

    /**
     * Gets the {@link DigitalSignatureAndMetadata} signatures that have been applied to the current transaction.
     *
     * @return Returns the {@link DigitalSignatureAndMetadata} signatures that have been applied to the current transaction.
     */
    @NotNull
    List<DigitalSignatureAndMetadata> getSignatures();

    /**
     * Gets the {@link StateRef} instances of the inputs for the current transaction.
     *
     * @return Returns the {@link StateRef} instances of the inputs for the current transaction.
     */
    @NotNull
    List<StateRef> getInputStateRefs();

    /**
     * Gets the {@link StateRef} instances of the reference inputs for the current transaction.
     *
     * @return Returns the {@link StateRef} instances of the reference inputs for the current transaction.
     */
    @NotNull
    List<StateRef> getReferenceStateRefs();

    /**
     * Gets the {@link StateAndRef} instances of the outputs for the current transaction.
     *
     * @return Returns the {@link StateAndRef} instances of the outputs for the current transaction.
     */
    @NotNull
    List<StateAndRef<?>> getOutputStateAndRefs();

    /**
     * Gets the notary {@link Party} used for notarising the current transaction.
     *
     * @return Returns the notary {@link Party} used for notarising the current transaction.
     */
    @NotNull
    Party getNotary();

    /**
     * Gets the validity {@link TimeWindow} for notarising and finalizing the current transaction.
     *
     * @return Returns the validity {@link TimeWindow} for notarising and finalizing the current transaction.
     */
    @NotNull
    TimeWindow getTimeWindow();

    /**
     * Gets the {@link Command} instances for the current transaction.
     *
     * @return Returns the {@link Command} instances for the current transaction.
     */
    @NotNull
    List<Command> getCommands();

    /**
     * Gets the {@link PublicKey} instances for the signatories of the current transaction.
     *
     * @return Returns the {@link PublicKey} instances for the signatories of the current transaction.
     */
    @NotNull
    List<PublicKey> getSignatories();

    /**
     * Converts the current {@link UtxoSignedTransaction} into a {@link UtxoLedgerTransaction}.
     *
     * @return Returns a {@link UtxoLedgerTransaction} from the current {@link UtxoSignedTransaction}.
     */
    @NotNull
    @Suspendable
    UtxoLedgerTransaction toLedgerTransaction();
}
