package net.corda.v5.ledger.utxo.transaction;

import net.corda.v5.base.annotations.DoNotImplement;
import net.corda.v5.crypto.SecureHash;
import net.corda.v5.ledger.common.Party;
import net.corda.v5.ledger.common.transaction.TransactionMetadata;
import net.corda.v5.ledger.utxo.Attachment;
import net.corda.v5.ledger.utxo.Command;
import net.corda.v5.ledger.utxo.ContractState;
import net.corda.v5.ledger.utxo.StateAndRef;
import net.corda.v5.ledger.utxo.StateRef;
import net.corda.v5.ledger.utxo.TimeWindow;
import net.corda.v5.ledger.utxo.TransactionState;
import org.jetbrains.annotations.NotNull;

import java.security.PublicKey;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a UTXO ledger transaction.
 */
@DoNotImplement
@SuppressWarnings("TooManyFunctions")
public interface UtxoLedgerTransaction {

    /**
     * Gets the transaction ID associated with the current {@link UtxoLedgerTransaction}.
     *
     * @return Returns the transaction ID associated with the current {@link UtxoLedgerTransaction}.
     */
    @NotNull
    SecureHash getId();

    /**
     * Gets the notary associated with the current {@link UtxoLedgerTransaction}.
     *
     * @return Returns the notary associated with the current {@link UtxoLedgerTransaction}.
     */
    @NotNull
    Party getNotary();

    /**
     * Gets the transaction metadata associated with the current {@link UtxoLedgerTransaction}.
     *
     * @return Returns the transaction metadata associated with the current {@link UtxoLedgerTransaction}.
     */
    @NotNull
    TransactionMetadata getMetadata();

    /**
     * Gets the time window associated with the current {@link UtxoLedgerTransaction}.
     *
     * @return Returns the time window associated with the current {@link UtxoLedgerTransaction}.
     */
    @NotNull
    TimeWindow getTimeWindow();

    /**
     * Gets the signatories associated with the current {@link UtxoLedgerTransaction}.
     *
     * @return Returns the signatories associated with the current {@link UtxoLedgerTransaction}.
     */
    @NotNull
    List<PublicKey> getSignatories();

    /**
     * Gets the attachments associated with the current {@link UtxoLedgerTransaction}.
     *
     * @return Returns the attachments associated with the current {@link UtxoLedgerTransaction}.
     */
    @NotNull
    List<Attachment> getAttachments();

    /**
     * Obtains an attachment associated with the current {@link UtxoLedgerTransaction}.
     *
     * @param id The ID of the attachment to obtain.
     * @return Returns the attachment with the specified ID.
     * @throws IllegalArgumentException if the attachment with the specified ID cannot be found.
     */
    @NotNull
    Attachment getAttachment(@NotNull SecureHash id);

    /**
     * Gets the commands associated with the current {@link UtxoLedgerTransaction}.
     *
     * @return Returns the commands associated with the current {@link UtxoLedgerTransaction}.
     */
    @NotNull
    List<Command> getCommands();

    /**
     * Obtains all commands that match the specified type.
     *
     * @param <T>  The underlying type of the {@link Command}.
     * @param type The type of the {@link Command}.
     * @return Returns all commands that match the specified type.
     */
    @NotNull
    <T extends Command> List<T> getCommands(@NotNull Class<T> type);

    /**
     * Gets the input state refs associated with the current {@link UtxoLedgerTransaction}.
     *
     * @return Returns the input state refs associated with the current {@link UtxoLedgerTransaction}.
     */
    @NotNull
    List<StateRef> getInputStateRefs();

    /**
     * Gets the input states and state refs associated with the current {@link UtxoLedgerTransaction}.
     *
     * @return Returns the input states and state refs associated with the current {@link UtxoLedgerTransaction}.
     */
    @NotNull
    List<StateAndRef<?>> getInputStateAndRefs();

    /**
     * Obtains all input states and state refs that match the specified type.
     *
     * @param <T>  The underlying type of the {@link ContractState}.
     * @param type The type of the {@link ContractState}.
     * @return Returns all input states and state refs that match the specified type.
     */
    @NotNull
    <T extends ContractState> List<StateAndRef<T>> getInputStateAndRefs(@NotNull Class<T> type);

    /**
     * Gets the input transaction states associated with the current {@link UtxoLedgerTransaction}.
     *
     * @return Returns the input transaction states associated with the current {@link UtxoLedgerTransaction}.
     */
    @NotNull
    default List<TransactionState<?>> getInputTransactionStates() {
        return getInputStateAndRefs()
                .stream()
                .map(StateAndRef::getState)
                .collect(Collectors.toList());
    }

    /**
     * Gets the input contract states associated with the current {@link UtxoLedgerTransaction}.
     *
     * @return Returns the input contract states associated with the current {@link UtxoLedgerTransaction}.
     */
    @NotNull
    default List<ContractState> getInputContractStates() {
        return getInputTransactionStates()
                .stream()
                .map(TransactionState::getContractState)
                .collect(Collectors.toList());
    }

    /**
     * Obtains all input contract states that match the specified type.
     *
     * @param <T>  The underlying type of the {@link ContractState}.
     * @param type The type of the {@link ContractState}.
     * @return Returns all input contract states that match the specified type.
     */
    // TODO : Rename to getInputContractStates
    @NotNull
    <T extends ContractState> List<T> getInputStates(@NotNull Class<T> type);

    /**
     * Gets the reference input state refs associated with the current {@link UtxoLedgerTransaction}.
     *
     * @return Returns the reference input state refs associated with the current {@link UtxoLedgerTransaction}.
     */
    @NotNull
    List<StateRef> getReferenceStateRefs();

    /**
     * Gets the reference input states and state refs associated with the current {@link UtxoLedgerTransaction}.
     *
     * @return Returns the reference input states and state refs associated with the current {@link UtxoLedgerTransaction}.
     */
    @NotNull
    List<StateAndRef<?>> getReferenceStateAndRefs();

    /**
     * Obtains all reference input states and state refs that match the specified type.
     *
     * @param <T>  The underlying type of the {@link ContractState}.
     * @param type The type of the {@link ContractState}.
     * @return Returns all reference input states and state refs that match the specified type.
     */
    @NotNull
    <T extends ContractState> List<StateAndRef<T>> getReferenceStateAndRefs(@NotNull Class<T> type);

    /**
     * Gets the reference input transaction states associated with the current {@link UtxoLedgerTransaction}.
     *
     * @return Returns the reference input transaction states associated with the current {@link UtxoLedgerTransaction}.
     */
    @NotNull
    default List<TransactionState<?>> getReferenceTransactionStates() {
        return getReferenceStateAndRefs()
                .stream()
                .map(StateAndRef::getState)
                .collect(Collectors.toList());
    }

    /**
     * Gets the reference input contract states associated with the current {@link UtxoLedgerTransaction}.
     *
     * @return Returns the reference input contract states associated with the current {@link UtxoLedgerTransaction}.
     */
    @NotNull
    default List<ContractState> getReferenceContractStates() {
        return getReferenceTransactionStates()
                .stream()
                .map(TransactionState::getContractState)
                .collect(Collectors.toList());
    }

    /**
     * Obtains all reference input contract states that match the specified type.
     *
     * @param <T>  The underlying type of the {@link ContractState}.
     * @param type The type of the {@link ContractState}.
     * @return Returns all reference input contract states that match the specified type.
     */
    @NotNull
    <T extends ContractState> List<T> getReferenceStates(@NotNull Class<T> type);

    /**
     * Gets the output states and state refs associated with the current {@link UtxoLedgerTransaction}.
     *
     * @return Returns the output states and state refs associated with the current {@link UtxoLedgerTransaction}.
     */
    @NotNull
    List<StateAndRef<?>> getOutputStateAndRefs();

    /**
     * Obtains all output states and state refs that match the specified type.
     *
     * @param <T>  The underlying type of the {@link ContractState}.
     * @param type The type of the {@link ContractState}.
     * @return Returns all output states and state refs that match the specified type.
     */
    @NotNull
    <T extends ContractState> List<StateAndRef<T>> getOutputStateAndRefs(@NotNull Class<T> type);

    /**
     * Gets the output transaction states associated with the current {@link UtxoLedgerTransaction}.
     *
     * @return Returns the output transaction states associated with the current {@link UtxoLedgerTransaction}.
     */
    @NotNull
    default List<TransactionState<?>> getOutputTransactionStates() {
        return getOutputStateAndRefs()
                .stream()
                .map(StateAndRef::getState)
                .collect(Collectors.toList());
    }

    /**
     * Gets the output contract states associated with the current {@link UtxoLedgerTransaction}.
     *
     * @return Returns the output contract states associated with the current {@link UtxoLedgerTransaction}.
     */
    @NotNull
    default List<ContractState> getOutputContractStates() {
        return getOutputTransactionStates()
                .stream()
                .map(TransactionState::getContractState)
                .collect(Collectors.toList());
    }

    /**
     * Obtains all output contract states that match the specified type.
     *
     * @param <T>  The underlying type of the {@link ContractState}.
     * @param type The type of the {@link ContractState}.
     * @return Returns all output contract states that match the specified type.
     */
    // TODO : Rename to getOutputContractStates
    @NotNull
    <T extends ContractState> List<T> getOutputStates(@NotNull Class<T> type);
}
