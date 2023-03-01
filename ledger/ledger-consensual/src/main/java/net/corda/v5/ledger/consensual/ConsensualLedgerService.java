package net.corda.v5.ledger.consensual;

import net.corda.v5.application.messaging.FlowSession;
import net.corda.v5.base.annotations.DoNotImplement;
import net.corda.v5.base.annotations.Suspendable;
import net.corda.v5.crypto.SecureHash;
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction;
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction;
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder;
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionValidator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Defines the consensual ledger service.
 */
@DoNotImplement
public interface ConsensualLedgerService {

    /**
     * Gets a {@link ConsensualTransactionBuilder} instance.
     *
     * @return Returns a new {@link ConsensualTransactionBuilder} instance.
     */
    @NotNull
    @Suspendable
    ConsensualTransactionBuilder getTransactionBuilder();

    /**
     * Finds a {@link ConsensualSignedTransaction} in the vault by its transaction ID.
     *
     * @param id The transaction ID of the {@link ConsensualSignedTransaction} to find in the vault.
     * @return Returns the {@link ConsensualSignedTransaction} if it has been recorded, or null if the transaction could not be found.
     */
    @Nullable
    @Suspendable
    ConsensualSignedTransaction findSignedTransaction(@NotNull SecureHash id);

    /**
     * Finds a {@link ConsensualLedgerTransaction} in the vault by its transaction ID.
     *
     * @param id The transaction ID of the {@link ConsensualLedgerTransaction} to find in the vault.
     * @return Returns the {@link ConsensualLedgerTransaction} if it has been recorded, or null if the transaction could not be found.
     */
    @Nullable
    @Suspendable
    ConsensualLedgerTransaction findLedgerTransaction(@NotNull SecureHash id);

    /**
     * Finalizes a transaction by collecting any remaining required signatures from counter-parties, and broadcasts the
     * fully signed transaction to all participants involved in the transaction to be recorded in the vault.
     *
     * @param transaction The transaction to finalize.
     * @param sessions The sessions representing the counter-party participants of the transaction.
     * @return Returns the fully signed and recorded transaction.
     */
    @NotNull
    @Suspendable
    ConsensualSignedTransaction finalize(
            @NotNull ConsensualSignedTransaction transaction,
            @NotNull List<FlowSession> sessions
    );

    /**
     * Verifies, signs and records the fully signed {@link ConsensualSignedTransaction}.
     *
     * @param session The session from which the {@link ConsensualSignedTransaction} was received.
     * @param validator Validates the received {@link ConsensualSignedTransaction}.
     * @return Returns the fully signed {@link ConsensualSignedTransaction}.
     */
    @NotNull
    @Suspendable
    ConsensualSignedTransaction receiveFinality(
            @NotNull FlowSession session,
            @NotNull ConsensualTransactionValidator validator
    );
}
