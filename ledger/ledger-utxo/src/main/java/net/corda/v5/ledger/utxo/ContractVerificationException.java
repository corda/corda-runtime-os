package net.corda.v5.ledger.utxo;

import net.corda.v5.base.exceptions.CordaRuntimeException;
import net.corda.v5.crypto.SecureHash;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Represents the exception that is thrown for a contract verification failure.
 */
public final class ContractVerificationException extends CordaRuntimeException {

    /**
     * The transaction ID of the transaction that has failed verification.
     */
    @NotNull
    private final SecureHash transactionId;

    /**
     * The list of failure reasons for the transaction that has failed verification.
     */
    @NotNull
    private final List<ContractVerificationFailure> failureReasons;

    /**
     * Creates a new instance of the {@link ContractVerificationException} class.
     *
     * @param transactionId  The transaction ID of the transaction that has failed verification.
     * @param failureReasons The list of failure reasons for the transaction that has failed verification.
     */
    public ContractVerificationException(
            @NotNull final SecureHash transactionId,
            @NotNull final List<ContractVerificationFailure> failureReasons) {
        super(buildExceptionMessage(transactionId, failureReasons));
        this.transactionId = transactionId;
        this.failureReasons = failureReasons;
    }

    /**
     * Gets the transaction ID of the transaction that has failed verification.
     *
     * @return Returns the transaction ID of the transaction that has failed verification.
     */
    @NotNull
    public SecureHash getTransactionId() {
        return transactionId;
    }

    /**
     * Gets the list of failure reasons for the transaction that has failed verification.
     *
     * @return Returns the list of failure reasons for the transaction that has failed verification.
     */
    @NotNull
    public List<ContractVerificationFailure> getFailureReasons() {
        return failureReasons;
    }

    /**
     * Builds a detailed exception message containing the transaction failure reasons.
     *
     * @param transactionId  The transaction ID of the transaction that has failed verification.
     * @param failureReasons The list of failure reasons for the transaction that has failed verification.
     * @return Returns a detailed exception message containing the transaction failure reasons.
     */
    private static String buildExceptionMessage(
            @NotNull final SecureHash transactionId,
            @NotNull final List<ContractVerificationFailure> failureReasons) {
        StringBuilder builder = new StringBuilder();

        builder.append("Ledger transaction contract verification failed for the specified transaction: ");
        builder.append(transactionId);
        builder.append(".\n");
        builder.append("The following contract verification requirements were not met:\n");

        for (ContractVerificationFailure failure : failureReasons) {
            builder.append(failure);
            builder.append('\n');
        }

        return builder.toString();
    }
}
