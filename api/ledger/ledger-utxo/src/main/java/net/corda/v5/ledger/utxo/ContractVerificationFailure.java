package net.corda.v5.ledger.utxo;

import net.corda.v5.base.annotations.CordaSerializable;
import net.corda.v5.base.annotations.DoNotImplement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Defines a contract verification failure.
 */
@DoNotImplement
@CordaSerializable
public interface ContractVerificationFailure {

    /**
     * Gets the class name of the {@link Contract} that caused verification failure.
     *
     * @return Returns the class name of the {@link Contract} that caused verification failure.
     */
    @NotNull
    String getContractClassName();

    /**
     * Gets the class names of the contract states that import the {@link Contract} that caused verification failure.
     *
     * @return Returns the class names of the contract states that import the {@link Contract} that caused verification failure.
     */
    @NotNull
    List<String> getContractStateClassNames();

    /**
     * Gets the class name of the {@link Exception} that caused verification failure.
     *
     * @return Returns the class name of the {@link Exception} that caused verification failure.
     */
    @NotNull
    String getExceptionClassName();

    /**
     * Gets the details of the {@link Exception} that caused verification failure.
     *
     * @return Returns the details of the {@link Exception} that caused verification failure.
     */
    @NotNull
    String getExceptionMessage();
}
