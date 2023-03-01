package net.corda.v5.ledger.utxo.uniqueness.client;

import net.corda.v5.application.uniqueness.model.UniquenessCheckResult;
import net.corda.v5.base.annotations.DoNotImplement;
import net.corda.v5.base.annotations.Suspendable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * Defines a service that is used to request uniqueness checking.
 * The request will be processed asynchronously and a Future will be returned that can be used to check the outcome.
 * This service can be injected to either flows or other services.
 */
@DoNotImplement
public interface LedgerUniquenessCheckerClientService {

    /**
     * Requests a uniqueness check.
     *
     * @param transactionId The ID of the transaction to be processed.
     * @param inputStates A list of the input state refs that belongs to the given transaction.
     * @param referenceStates A list of the reference state refs that belongs to the given transaction.
     * @param numOutputStates The number of output states in the given transaction.
     * @param timeWindowLowerBound The earliest date/time from which the transaction is considered valid.
     * @param timeWindowUpperBound The latest date/time until the transaction is considered valid.
     * @return Returns the result that was produced by the uniqueness checker.
     */
    @Suspendable
    @SuppressWarnings("LongParameterList")
    UniquenessCheckResult requestUniquenessCheck(
            @NotNull String transactionId,
            @NotNull List<String> inputStates,
            @NotNull List<String> referenceStates,
            int numOutputStates,
            @Nullable Instant timeWindowLowerBound,
            @NotNull Instant timeWindowUpperBound
    );
}
