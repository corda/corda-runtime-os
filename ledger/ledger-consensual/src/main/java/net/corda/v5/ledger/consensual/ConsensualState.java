package net.corda.v5.ledger.consensual;

import net.corda.v5.base.annotations.CordaSerializable;
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction;
import org.jetbrains.annotations.NotNull;

import java.security.PublicKey;
import java.util.List;

/**
 * Defines a consensual state.
 * <p>
 * A consensual state (or just "state") contains opaque data used by a consensual ledger. It can be thought of as a
 * disk file that the program can use to persist data across transactions. They are immutable, and can never be
 * consumed. Once created they are never updated, instead, any changes must generate a new successor state.
 */
@CordaSerializable
public interface ConsensualState {

    /**
     * Gets a list of the current consensual state's participants.
     * <p>
     * A participant is any party whose consent is needed to make a consensual state valid and final.
     * Participants are the main and only verification points for Consensual state since they do not have contract code.
     * Every participant has to be involved and approve the transaction so that they receive the updated state,
     * and don't end up in a situation where they can no longer use a state they possess.
     * <p>
     * The participants list should normally be derived from the contents of the state.
     *
     * @return Returns a list of the current consensual state's participants.
     */
    @NotNull
    List<PublicKey> getParticipants();

    /**
     * Verifies the current consensual state.
     * <p>
     * Implementations of this function should:
     * - verify that the state is well-formed.
     * - verify compatibility or cohesion with other states in the specified transaction.
     * - check for required signing keys.
     * - check the transaction's timestamp.
     * <p>
     * TODO : CORE-5995 - Make services injectable (e.g. crypto, etc...)
     *
     * @param transaction The transaction in which the current consensual state will be created.
     */
    void verify(@NotNull ConsensualLedgerTransaction transaction);
}
