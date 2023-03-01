package net.corda.v5.ledger.utxo;

import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction;
import org.jetbrains.annotations.NotNull;

import java.security.PublicKey;
import java.util.Set;

/**
 * Defines a mechanism for implementing contracts, which perform transaction verification.
 * <p>
 * All participants run this code for every input and output state for every transaction they see on the network.
 * All contracts must verify and accept the associated transaction for it to be finalized and persisted to the ledger.
 * Failure of any contract constraint aborts the transaction, resulting in the transaction not being finalized.
 */
public interface Contract {

    /**
     * TODO : Still in discussion - what do we call this, and where does it live?
     * Determines whether a given state is relevant to a node, given the node's public keys.
     * <p>
     * With default implementation, a state is relevant if any of the participants key matching one of this node's
     * public keys.
     */
    default boolean isRelevant(@NotNull ContractState state, @NotNull Set<PublicKey> myKeys) {
        return state.getParticipants().stream().anyMatch(myKeys::contains);
    }

    /**
     * Verifies the specified transaction associated with the current contract.
     *
     * @param transaction The transaction to verify.
     * @throws RuntimeException if the specified transaction fails verification.
     */
    void verify(@NotNull UtxoLedgerTransaction transaction);
}
