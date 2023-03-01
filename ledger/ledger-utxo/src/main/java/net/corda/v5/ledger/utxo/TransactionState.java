package net.corda.v5.ledger.utxo;

import net.corda.v5.base.annotations.CordaSerializable;
import net.corda.v5.base.annotations.DoNotImplement;
import net.corda.v5.ledger.common.Party;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Defines a transaction state, composed of a {@link ContractState} and associated transaction state information.
 *
 * @param <T> The underlying type of the {@link ContractState} instance.
 */
@DoNotImplement
@CordaSerializable
public interface TransactionState<T extends ContractState> {

    /**
     * Gets the {@link ContractState} of the current {@link TransactionState} instance.
     *
     * @return Returns the {@link ContractState} of the current {@link TransactionState} instance.
     */
    @NotNull
    T getContractState();

    /**
     * Gets the {@link ContractState} type of the current {@link TransactionState} instance.
     *
     * @return Returns the {@link ContractState} type of the current {@link TransactionState} instance.
     */
    @NotNull
    Class<T> getContractStateType();

    /**
     * Gets the {@link Contract} type of the current {@link TransactionState} instance.
     *
     * @return Returns the {@link Contract} type of the current {@link TransactionState} instance.
     */
    @NotNull
    Class<? extends Contract> getContractType();

    /**
     * Gets the notary of the current {@link TransactionState} instance.
     *
     * @return Returns the notary of the current {@link TransactionState} instance.
     */
    @NotNull
    Party getNotary();

    /**
     * Gets the encumbrance of the current {@link TransactionState} instance.
     *
     * @return Returns the encumbrance of the current {@link TransactionState} instance.
     */
    @Nullable
    EncumbranceGroup getEncumbranceGroup();
}
