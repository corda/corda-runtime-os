package net.corda.v5.ledger.consensual.transaction;

import net.corda.v5.base.annotations.DoNotImplement;
import net.corda.v5.crypto.SecureHash;
import net.corda.v5.ledger.consensual.ConsensualState;
import org.jetbrains.annotations.NotNull;

import java.security.PublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Defines a consensual ledger transaction.
 * <p>
 * Comparing with {@link ConsensualSignedTransaction}:
 * - It has access to the deserialized details.
 * - It does not have direct access to the signatures.
 * - It requires a serializer.
 * <p>
 * {@link ConsensualLedgerTransaction} is an abstraction that is meant to be used during the transaction verification
 * stage. It needs full access to its states that might be in transactions that are encrypted and unavailable for code
 * running outside the secure enclave. Also, it might need to deserialize states with code that might not be available
 * on the classpath.
 * <p>
 * Because of this, trying to create or use a {@link ConsensualLedgerTransaction} for any other purpose then transaction
 * verification can result in unexpected exceptions, which need de be handled.
 * <p>
 * {@link ConsensualLedgerTransaction} should never be instantiated directly from client code, but rather via
 * {@link ConsensualSignedTransaction#toLedgerTransaction()}
 */
@DoNotImplement
public interface ConsensualLedgerTransaction {

    /**
     * Gets the ID of the current transaction.
     *
     * @return Returns the ID of the current transaction.
     */
    @NotNull
    SecureHash getId();

    /**
     * Gets a set of signatories that are required for transaction validity;
     * essentially the union of the participants of the current transaction's states.
     *
     * @return Return a set of signatories that are required for transaction validity.
     */
    @NotNull
    Set<PublicKey> getRequiredSignatories();

    /**
     * Gets the timestamp of the current transaction, which is when it was initially signed.
     *
     * @return Returns the timestamp of the current transaction, which is when it was initially signed.
     */
    @NotNull
    Instant getTimestamp();

    /**
     * Gets the output states of the current transaction.
     *
     * @return Returns the output states of the current transaction.
     */
    @NotNull
    List<ConsensualState> getStates();
}
