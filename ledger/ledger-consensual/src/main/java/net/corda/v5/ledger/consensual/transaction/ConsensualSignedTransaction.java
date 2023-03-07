package net.corda.v5.ledger.consensual.transaction;

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata;
import net.corda.v5.base.annotations.CordaSerializable;
import net.corda.v5.base.annotations.DoNotImplement;
import net.corda.v5.crypto.SecureHash;
import net.corda.v5.ledger.common.transaction.TransactionWithMetadata;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Defines a consensual signed transaction.
 * <p>
 * Comparing with {@link ConsensualLedgerTransaction}:
 * <ul>
 *     <li>It does not have access to the deserialized details.</li>
 *     <li>It has direct access to the signatures.</li>
 *     <li>It does not require a serializer.</li>
 * </ul>
 * <p>
 * {@link ConsensualSignedTransaction} wraps the wire representation of the transaction, which contains one or more
 * signatures, each one for a public key (including composite keys) that is mentioned inside a transaction state.
 * <p>
 * {@link ConsensualSignedTransaction} is frequently passed around the network and stored.
 * The identity of a transaction is the hash of Merkle root of the wrapped wire representation, therefore if you are
 * storing data keyed by wire representations hash be aware that multiple different {@link ConsensualSignedTransaction}s
 * may map to the same key (and they could be different in important ways, like validity).
 * <p>
 * The signatures on a {@link ConsensualSignedTransaction} might be invalid or missing: the type does not imply validity.
 * <p>
 * A transaction ID should be the hash of the wrapped wire representation's Merkle tree root.
 * <p>
 * Adding or removing a signature does not change the transaction ID.
 */
@CordaSerializable
@DoNotImplement
public interface ConsensualSignedTransaction extends TransactionWithMetadata {

    /**
     * Gets the signatures that have been applied to the current transaction.
     *
     * @return Returns the signatures that have been applied to the current transaction.
     */
    @NotNull
    List<DigitalSignatureAndMetadata> getSignatures();

    /**
     * Converts the current {@link ConsensualSignedTransaction} into a {@link ConsensualLedgerTransaction}.
     *
     * @return Returns a {@link ConsensualLedgerTransaction} from the current {@link ConsensualSignedTransaction}.
     */
    @NotNull
    ConsensualLedgerTransaction toLedgerTransaction();
}
