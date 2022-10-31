package net.corda.v5.ledger.utxo.observer;

import net.corda.v5.ledger.utxo.StateAndRef;
import org.jetbrains.annotations.NotNull;

/**
 * This tests validates the code example in the KDoc comments will compile
 */

public class UtxoLedgerTokenStateObserverJavaExample implements UtxoLedgerTokenStateObserver<ExampleStateK> {

    @NotNull
    @Override
    public Class<ExampleStateK> getStateType() {
        return ExampleStateK.class;
    }

    @NotNull
    @Override
    public UtxoToken onProduced(@NotNull StateAndRef<? extends ExampleStateK> stateAndRef) {
        ExampleStateK state = stateAndRef.getState().getContractState();

        return new UtxoToken(
                new UtxoTokenPoolKey(ExampleStateK.class.getName(), state.getIssuer(), state.getCurrency()),
                state.getAmount(),
                new UtxoTokenFilterFields()
        );
    }
}