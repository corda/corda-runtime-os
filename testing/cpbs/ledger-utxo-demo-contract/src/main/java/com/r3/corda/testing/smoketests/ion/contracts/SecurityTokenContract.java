package com.r3.corda.testing.smoketests.ion.contracts;

import net.corda.v5.ledger.utxo.Command;
import net.corda.v5.ledger.utxo.Contract;
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction;
import org.jetbrains.annotations.NotNull;

public class SecurityTokenContract implements Contract {

    public static class Create implements Command { }
    public static class Transfer implements Command { }

    @Override
    public void verify(@NotNull UtxoLedgerTransaction transaction) {
    }
}
