package net.corda.v5.ledger.utxo;

import net.corda.v5.application.messaging.FlowSession;
import net.corda.v5.base.annotations.DoNotImplement;
import net.corda.v5.base.annotations.Suspendable;
import net.corda.v5.crypto.SecureHash;
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction;
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction;
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder;
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionValidator;
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransaction;
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransactionBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Defines UTXO ledger services.
 */
@DoNotImplement
public interface UtxoLedgerService {

    /**
     * Gets a UTXO transaction builder
     *
     * @return Returns a new {@link UtxoTransactionBuilder} instance.
     */
    @NotNull
    @Suspendable
    UtxoTransactionBuilder getTransactionBuilder();

    /**
     * Resolves the specified {@link StateRef} instances into {@link StateAndRef} instances of the specified {@link ContractState} type.
     *
     * @param <T> The underlying {@link ContractState} type.
     * @param stateRefs The {@link StateRef} instances to resolve.
     * @return Returns a {@link List} of {@link StateAndRef} of the specified {@link ContractState} type.
     */
    @NotNull
    @Suspendable
    <T extends ContractState> List<StateAndRef<T>> resolve(@NotNull Iterable<StateRef> stateRefs);

    /**
     * Resolves the specified {@link StateRef} instance into a {@link StateAndRef} instance of the specified {@link ContractState} type.
     *
     * @param <T> The underlying {@link ContractState} type.
     * @param stateRef The {@link StateRef} instances to resolve.
     * @return Returns a {@link StateAndRef} of the specified {@link ContractState} type.
     */
    @NotNull
    @Suspendable
    <T extends ContractState> StateAndRef<T> resolve(@NotNull StateRef stateRef);

    /**
     * Finds a {@link UtxoSignedTransaction} in the vault by the specified transaction ID.
     *
     * @param id The ID of the {@link UtxoSignedTransaction} to find.
     * @return Returns the {@link UtxoSignedTransaction} if it has been recorded previously, or null if no transaction could be found.
     */
    @Nullable
    @Suspendable
    UtxoSignedTransaction findSignedTransaction(@NotNull SecureHash id);

    /**
     * Finds a {@link UtxoLedgerTransaction} in the vault by the specified transaction ID.
     *
     * @param id The ID of the {@link UtxoLedgerTransaction} to find.
     * @return Returns the {@link UtxoLedgerTransaction} if it has been recorded previously, or null if no transaction could be found.
     */
    @Nullable
    @Suspendable
    UtxoLedgerTransaction findLedgerTransaction(@NotNull SecureHash id);

    /**
     * Filters a {@link UtxoSignedTransaction} to create a {@link UtxoFilteredTransaction} that only contains the components specified by the
     * {@link UtxoFilteredTransactionBuilder} output from this method.
     *
     * @param transaction The {@link UtxoSignedTransaction} to filter.
     * @return Returns the {@link UtxoFilteredTransactionBuilder} that filters the {@link UtxoSignedTransaction}.
     */
    @NotNull
    @Suspendable
    UtxoFilteredTransactionBuilder filterSignedTransaction(@NotNull UtxoSignedTransaction transaction);

    /**
     * Finds unconsumed states of the specified {@link ContractState} type in the vault.
     *
     * @param <T>  The underlying {@link ContractState} type.
     * @param type The {@link ContractState} type to find in the vault.
     * @return Returns a {@link List} of {@link StateAndRef} of unconsumed states of the specified type, or an empty list if no states could be found.
     */
    @NotNull
    @Suspendable
    <T extends ContractState> List<StateAndRef<T>> findUnconsumedStatesByType(@NotNull Class<T> type);

    /**
     * Verifies, signs, collects signatures, records and broadcasts a {@link UtxoSignedTransaction} to participants and observers.
     *
     * @param transaction The {@link UtxoSignedTransaction} to verify, finalize and record.
     * @param sessions The {@link FlowSession} instances of the participants or observers of the transaction.
     * @return Returns the fully signed {@link UtxoSignedTransaction} that was recorded.
     * @throws ContractVerificationException if the transaction fails contract verification.
     */
    @NotNull
    @Suspendable
    UtxoSignedTransaction finalize(
            @NotNull UtxoSignedTransaction transaction,
            @NotNull List<FlowSession> sessions
    );

    /**
     * Verifies, signs and records a {@link UtxoSignedTransaction}.
     * <p>
     * This method should be called in response to {@link #finalize(UtxoSignedTransaction, List)}.
     *
     * @param session The {@link FlowSession} of the counter-party finalizing the {@link UtxoSignedTransaction}.
     * @param validator Validates the received {@link UtxoSignedTransaction}.
     * @return Returns the fully signed {@link UtxoSignedTransaction} that was received and recorded.
     * @throws ContractVerificationException if the transaction failed contract verification.
     */
    @NotNull
    @Suspendable
    UtxoSignedTransaction receiveFinality(
            @NotNull FlowSession session,
            @NotNull UtxoTransactionValidator validator
    );

    /**
     * Sends a transaction builder to another session, then it waits for other side to propose transaction builder components,
     * then applies the proposed components to a copy of the original builder and returns that new builder.
     * <p>
     * It supports similar workflows:
     * Initiator:
     * <p>
     * val updatedTxBuilder = utxoLedgerService.sendAndReceiveTransactionBuilder(txBuilder, session)
     * <p>
     * The notary and time window from the proposal will get discarded and the original will be kept if both the original and
     * the proposal have these components set. Also, all duplications will be discarded.
     * <p>
     * Receiver:
     * <p>
     * val proposedTxBuilder = utxoLedgerService.receiveTransactionBuilder(session)
     * proposedTxBuilder.add...(...)
     * proposedTxBuilder.add...(...)
     * proposedTxBuilder.add...(...)
     * utxoLedgerService.replyTransactionBuilderProposal(proposedTxBuilder, session)
     *
     * @param transactionBuilder The {@link UtxoTransactionBuilder} to send.
     * @param session The receiver {@link FlowSession]}.
     *
     * @return A new merged builder of the original and proposed components.
     */
    @NotNull
    @Suspendable
    UtxoTransactionBuilder sendAndReceiveTransactionBuilder(
            @NotNull UtxoTransactionBuilder transactionBuilder,
            @NotNull FlowSession session
    );

    /**
     * Receives a transaction builder from another session.
     *
     * @param session The {@link FlowSession] to receive the {@link UtxoTransactionBuilder} from.
     */
    @NotNull
    @Suspendable
    UtxoTransactionBuilder receiveTransactionBuilder(
            @NotNull FlowSession session
    );

    /**
     * Sends the differences of transaction builders to another session with all dependent back chains.
     * It works only with {@link UtxoTransactionBuilder}s created from {@link #receiveTransactionBuilder(FlowSession)}
     * which track the differences internally.
     * If it is called with anything else, it throws [InvalidParameterException].
     * <p>
     * @param transactionBuilder The {@link UtxoTransactionBuilder} to send.
     * @param session The receiver {@link FlowSession}.
     */
    @Suspendable
    void sendUpdatedTransactionBuilder(
            @NotNull UtxoTransactionBuilder transactionBuilder,
            @NotNull FlowSession session
    );
}
