package net.corda.v5.ledger.consensual.transaction;

import net.corda.v5.application.messaging.FlowSession;
import net.corda.v5.base.annotations.Suspendable;
import net.corda.v5.base.exceptions.CordaRuntimeException;
import net.corda.v5.ledger.consensual.ConsensualLedgerService;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/**
 * Defines a functional interface that validates a {@link ConsensualLedgerTransaction}.
 * <p>
 * An implementation of {@link ConsensualTransactionValidator} can be passed to
 * {@link ConsensualLedgerService#receiveFinality(FlowSession, ConsensualTransactionValidator)} to
 * perform custom validation on the {@link ConsensualLedgerTransaction} received from the initiator of finality.
 * <p>
 * When validating a {@link ConsensualLedgerTransaction} throw either an {@link IllegalArgumentException},
 * {@link IllegalStateException} or {@link CordaRuntimeException} to indicate that the transaction is invalid.
 * <p>
 * This will lead to the termination of finality for the caller of
 * {@link ConsensualLedgerService#receiveFinality(FlowSession, ConsensualTransactionValidator)} and all
 * participants included in finalizing the transaction.
 * <p>
 * Other exceptions will still stop the progression of finality; however, the reason for the failure will not be
 * communicated to the initiator of finality.
 */
@FunctionalInterface
public interface ConsensualTransactionValidator extends Serializable {

    /**
     * Checks a {@link ConsensualLedgerTransaction} for validity.
     * <p>
     * Throw an {@link IllegalArgumentException}, {@link IllegalStateException} or {@link CordaRuntimeException}
     * to indicate that the transaction is invalid.
     *
     * @param transaction The transaction to check.
     * @throws Throwable if the {@link ConsensualLedgerTransaction} fails validation.
     */
    @Suspendable
    void checkTransaction(@NotNull ConsensualLedgerTransaction transaction);
}
