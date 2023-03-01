package net.corda.v5.ledger.utxo.transaction;

import net.corda.v5.base.annotations.DoNotImplement;
import net.corda.v5.base.annotations.Suspendable;
import net.corda.v5.crypto.SecureHash;
import net.corda.v5.ledger.common.Party;
import net.corda.v5.ledger.common.transaction.TransactionNoAvailableKeysException;
import net.corda.v5.ledger.utxo.Attachment;
import net.corda.v5.ledger.utxo.Command;
import net.corda.v5.ledger.utxo.ContractState;
import net.corda.v5.ledger.utxo.StateRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.PublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Defines a builder for UTXO transactions.
 */
@DoNotImplement
@SuppressWarnings("TooManyFunctions")
public interface UtxoTransactionBuilder {

    /**
     * Adds the specified {@link Attachment} to the current {@link UtxoTransactionBuilder}.
     *
     * @param attachmentId The ID of the {@link Attachment} to add to the current {@link UtxoTransactionBuilder}.
     * @return Returns the current {@link UtxoTransactionBuilder} with the specified {@link Attachment}.
     */
    @NotNull
    UtxoTransactionBuilder addAttachment(@NotNull SecureHash attachmentId);

    /**
     * Adds the specified command to the current {@link UtxoTransactionBuilder}.
     *
     * @param command The command to add to the current {@link UtxoTransactionBuilder}.
     * @return Returns the current {@link UtxoTransactionBuilder} including the specified command.
     */
    @NotNull
    UtxoTransactionBuilder addCommand(@NotNull Command command);

    /**
     * Adds the specified signatories to the current {@link UtxoTransactionBuilder}.
     *
     * @param signatories The signatories to add to the current {@link UtxoTransactionBuilder}.
     * @return Returns the current {@link UtxoTransactionBuilder} including the specified signatories.
     */
    @NotNull
    UtxoTransactionBuilder addSignatories(@NotNull Iterable<PublicKey> signatories);

    /**
     * Adds the specified signatories to the current {@link UtxoTransactionBuilder}.
     *
     * @param signatories The signatories to add to the current {@link UtxoTransactionBuilder}.
     * @return Returns the current {@link UtxoTransactionBuilder} including the specified signatories.
     */
    @NotNull
    UtxoTransactionBuilder addSignatories(@NotNull PublicKey... signatories);

    /**
     * Adds the specified input state to the current {@link UtxoTransactionBuilder}.
     *
     * @param stateRef The {@link StateRef} instance of the input state to add to the current {@link UtxoTransactionBuilder}.
     * @return Returns the current {@link UtxoTransactionBuilder} including the specified input state.
     */
    @NotNull
    UtxoTransactionBuilder addInputState(@NotNull StateRef stateRef);

    /**
     * Adds the specified input states to the current {@link UtxoTransactionBuilder}.
     *
     * @param stateRefs The {@link StateRef} instances of the input state to add to the current {@link UtxoTransactionBuilder}.
     * @return Returns the current {@link UtxoTransactionBuilder} including the specified input states.
     */
    @NotNull
    UtxoTransactionBuilder addInputStates(@NotNull Iterable<StateRef> stateRefs);

    /**
     * Adds the specified input states to the current {@link UtxoTransactionBuilder}.
     *
     * @param stateRefs The {@link StateRef} instances of the input state to add to the current {@link UtxoTransactionBuilder}.
     * @return Returns the current {@link UtxoTransactionBuilder} including the specified input states.
     */
    @NotNull
    UtxoTransactionBuilder addInputStates(@NotNull StateRef... stateRefs);

    /**
     * Adds the specified reference state to the current {@link UtxoTransactionBuilder}.
     *
     * @param stateRef The {@link StateRef} instance of the reference state to add to the current {@link UtxoTransactionBuilder}.
     * @return Returns the current {@link UtxoTransactionBuilder} including the specified reference states.
     */
    @NotNull
    UtxoTransactionBuilder addReferenceState(@NotNull StateRef stateRef);

    /**
     * Adds the specified reference states to the current {@link UtxoTransactionBuilder}.
     *
     * @param stateRefs The {@link StateRef} instances of the reference state to add to the current {@link UtxoTransactionBuilder}.
     * @return Returns the current {@link UtxoTransactionBuilder} including the specified reference states.
     */
    @NotNull
    UtxoTransactionBuilder addReferenceStates(@NotNull Iterable<StateRef> stateRefs);

    /**
     * Adds the specified reference states to the current {@link UtxoTransactionBuilder}.
     *
     * @param stateRefs The {@link StateRef} instances of the reference state to add to the current {@link UtxoTransactionBuilder}.
     * @return Returns the current {@link UtxoTransactionBuilder} including the specified reference states.
     */
    @NotNull
    UtxoTransactionBuilder addReferenceStates(@NotNull StateRef... stateRefs);

    /**
     * Adds the specified output state to the current {@link UtxoTransactionBuilder}.
     *
     * @param contractState The {@link ContractState} instance to add to the current {@link UtxoTransactionBuilder}.
     * @return Returns the current {@link UtxoTransactionBuilder} including the specified output states.
     */
    @NotNull
    UtxoTransactionBuilder addOutputState(@NotNull ContractState contractState);

    /**
     * Adds the specified output states to the current {@link UtxoTransactionBuilder}.
     *
     * @param contractStates The {@link ContractState} instances to add to the current {@link UtxoTransactionBuilder}.
     * @return Returns the current {@link UtxoTransactionBuilder} including the specified output states.
     */
    @NotNull
    UtxoTransactionBuilder addOutputStates(@NotNull Iterable<ContractState> contractStates);

    /**
     * Adds the specified output states to the current {@link UtxoTransactionBuilder}.
     *
     * @param contractStates The {@link ContractState} instances to add to the current {@link UtxoTransactionBuilder}.
     * @return Returns the current {@link UtxoTransactionBuilder} including the specified output states.
     */
    @NotNull
    UtxoTransactionBuilder addOutputStates(@NotNull ContractState... contractStates);

    /**
     * Adds the specified output states to the current {@link UtxoTransactionBuilder} as a tagged encumbrance group.
     *
     * @param tag            The tag of the encumbrance group which the specified {@link ContractState} instances will belong to.
     * @param contractStates The {@link ContractState} instances to add to the current {@link UtxoTransactionBuilder}.
     * @return Returns the current {@link UtxoTransactionBuilder} including the specified encumbered output states.
     */
    @NotNull
    UtxoTransactionBuilder addEncumberedOutputStates(@NotNull String tag, @NotNull Iterable<ContractState> contractStates);

    /**
     * Adds the specified output states to the current {@link UtxoTransactionBuilder} as a tagged encumbrance group.
     *
     * @param tag            The tag of the encumbrance group which the specified {@link ContractState} instances will belong to.
     * @param contractStates The {@link ContractState} instances to add to the current {@link UtxoTransactionBuilder}.
     * @return Returns the current {@link UtxoTransactionBuilder} including the specified encumbered output states.
     */
    @NotNull
    UtxoTransactionBuilder addEncumberedOutputStates(@NotNull String tag, @NotNull ContractState... contractStates);

    /**
     * Gets a list of encumbered {@link ContractState} instances from the specified encumbrance group tag.
     *
     * @param tag The encumbrance group tag for which to obtain the associated list of {@link ContractState} instances.
     * @return Returns a list of encumbered {@link ContractState} instances from the specified encumbrance group tag.
     * @throws IllegalArgumentException if the encumbrance group tag does not exist.
     */
    @NotNull
    List<ContractState> getEncumbranceGroup(@NotNull String tag);

    /**
     * Gets a map of encumbrance group tags and the associated encumbered {@link ContractState} instances.
     *
     * @return Returns map of encumbrance group tags and the associated encumbered {@link ContractState} instances.
     */
    @NotNull
    Map<String, List<ContractState>> getEncumbranceGroups();

    /**
     * Gets the notary assigned to the current transaction, or null if the notary has not been set.
     *
     * @return Returns the notary assigned to the current transaction, or null if the notary has not been set.
     */
    @Nullable
    Party getNotary();

    /**
     * Sets the specified {@link Party} as a notary to the current {@link UtxoTransactionBuilder}.
     *
     * @param notary The {@link Party} to set as a notary to the current {@link UtxoTransactionBuilder}.
     * @return Returns a new {@link UtxoTransactionBuilder} with the new notary.
     */
    @NotNull
    UtxoTransactionBuilder setNotary(@NotNull Party notary);

    /**
     * Sets the transaction time window to be valid until the specified {@link Instant}, tending towards negative infinity.
     *
     * @param until The {@link Instant} until which the transaction time window is valid.
     * @return Returns the current {@link UtxoTransactionBuilder} including the specified time window.
     */
    @NotNull
    UtxoTransactionBuilder setTimeWindowUntil(@NotNull Instant until);

    /**
     * Sets the transaction time window to be valid between the specified {@link Instant} values.
     *
     * @param from  The {@link Instant} from which the transaction time window is valid.
     * @param until The {@link Instant} until which the transaction time window is valid.
     * @return Returns the current {@link UtxoTransactionBuilder} including the specified time window.
     */
    @NotNull
    UtxoTransactionBuilder setTimeWindowBetween(@NotNull Instant from, @NotNull Instant until);

    /**
     * Verifies the content of the {@link UtxoTransactionBuilder} and
     * signs the transaction with any required signatories that belong to the current node.
     * <p>
     * Calling this function once consumes the {@link UtxoTransactionBuilder}, so it cannot be used again.
     * Therefore, if you want to build two transactions you need two builders.
     *
     * @return Returns a {@link UtxoSignedTransaction} with signatures for any required signatories that belong to the current node.
     * @throws IllegalStateException               when called a second time on the same object to prevent unintentional duplicate transactions.
     * @throws TransactionNoAvailableKeysException if none of the required keys are available to sign the transaction.
     */
    @NotNull
    @Suspendable
    UtxoSignedTransaction toSignedTransaction();
}
