package net.corda.v5.ledger.utxo.observer;

import net.corda.v5.ledger.utxo.StateAndRef;
import org.jetbrains.annotations.NotNull;

/**
 * This tests validates the code example in the KDoc comments will compile
 */

public class UtxoLedgerTokenStateObserverJavaExample implements UtxoLedgerTokenStateObserver<ExampleStateJ> {

    @NotNull
    @Override
    public Class<ExampleStateJ> getStateType() {
        return ExampleStateJ.class;
    }

    @NotNull
    @Override
    public UtxoToken onCommit(@NotNull ExampleStateJ state) {
        return new UtxoToken(
                new UtxoTokenPoolKey(ExampleStateK.class.getName(), state.issuer, state.currency),
                state.amount,
                new UtxoTokenFilterFields()
        );
    }
}