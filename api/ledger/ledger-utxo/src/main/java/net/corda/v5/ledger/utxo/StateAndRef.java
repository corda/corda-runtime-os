package net.corda.v5.ledger.utxo;

import net.corda.v5.base.annotations.CordaSerializable;
import net.corda.v5.base.annotations.DoNotImplement;
import org.jetbrains.annotations.NotNull;

/**
 * Defines a composition of a {@link TransactionState} and a {@link StateRef}.
 */
@DoNotImplement
@CordaSerializable
public interface StateAndRef<T extends ContractState> {

    /**
     * Gets the {@link TransactionState} component of the current {@link StateAndRef}.
     *
     * @return Returns the {@link TransactionState} component of the current {@link StateAndRef}.
     */
    @NotNull
    TransactionState<T> getState();

    /**
     * Gets the {@link StateRef} component of the current {@link StateAndRef}.
     *
     * @return Returns the {@link StateRef} component of the current {@link StateAndRef}.
     */
    @NotNull
    StateRef getRef();
}
