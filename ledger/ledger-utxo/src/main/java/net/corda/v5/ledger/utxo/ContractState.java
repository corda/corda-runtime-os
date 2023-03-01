package net.corda.v5.ledger.utxo;

import net.corda.v5.base.annotations.CordaSerializable;
import org.jetbrains.annotations.NotNull;

import java.security.PublicKey;
import java.util.List;

/**
 * Defines a contract state.
 * <p>
 * A contract state (or just "state") contains opaque data used by a contract program. It can be thought of as a disk
 * file that the program can use to persist data across transactions.
 * <p>
 * States are immutable. Once created they are never updated, instead, any changes must generate a new successor state.
 * States can be updated (consumed) only once. The notary is responsible for ensuring there is no "double spending" by
 * only signing a transaction if the input states are all free.
 */
@CordaSerializable
public interface ContractState {

    /**
     * Gets the public keys of any participants associated with the current contract state.
     *
     * @return Returns the public keys of any participants associated with the current contract state.
     */
    @NotNull
    List<PublicKey> getParticipants();
}
