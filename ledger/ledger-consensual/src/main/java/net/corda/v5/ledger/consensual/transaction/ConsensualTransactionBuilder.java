package net.corda.v5.ledger.consensual.transaction;

import net.corda.v5.base.annotations.Suspendable;
import net.corda.v5.ledger.common.transaction.TransactionNoAvailableKeysException;
import net.corda.v5.ledger.consensual.ConsensualState;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Defines a builder for {@link ConsensualSignedTransaction}.
 * <p>
 * The builder is designed to be mutable so that it can be passed around to counter-parties who may edit it by adding
 * new states. Once the states have been collected, the builder can be used to obtain a
 * {@link ConsensualSignedTransaction} which can be used to gather signatures from the transaction's participants.
 */
public interface ConsensualTransactionBuilder {

    /**
     * Gets the output states from the current {@link ConsensualTransactionBuilder}.
     *
     * @return Returns the output states from the current {@link ConsensualTransactionBuilder}.
     */
    @NotNull
    List<ConsensualState> getStates();

    /**
     * Adds the specified states to the current {@link ConsensualTransactionBuilder}.
     *
     * @param states The output states to add to the current {@link ConsensualTransactionBuilder}.
     * @return Returns a new {@link ConsensualTransactionBuilder} with the specified output states.
     */
    @NotNull
    // TODO : Consider alignment with UTXO addState and addStates.
    ConsensualTransactionBuilder withStates(ConsensualState... states);

    /**
     * Verifies the content of the current {@link ConsensualTransactionBuilder} and signs the transaction with any
     * required signatories that belong to the current node.
     * <p>
     * Calling this function once consumes the {@link ConsensualTransactionBuilder}, so it cannot be used again.
     * If you want to build two identical transactions, you will need two {@link ConsensualTransactionBuilder}s.
     *
     * @return Returns a {@link ConsensualSignedTransaction} with signatures for any required signatories that belong to the current node.
     * @throws IllegalStateException when called a second time on the same object to prevent unintentional duplicate transactions.
     * @throws TransactionNoAvailableKeysException if none of the required keys are available to sign the transaction.
     */
    @NotNull
    @Suspendable
    ConsensualSignedTransaction toSignedTransaction();
}
