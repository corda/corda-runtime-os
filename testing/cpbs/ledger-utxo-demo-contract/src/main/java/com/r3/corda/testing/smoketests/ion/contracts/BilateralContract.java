package com.r3.corda.testing.smoketests.ion.contracts;

import com.r3.corda.testing.smoketests.ion.states.BilateralState;
import com.r3.corda.testing.smoketests.ion.states.SecurityToken;
import net.corda.v5.base.exceptions.CordaRuntimeException;
import net.corda.v5.ledger.utxo.Command;
import net.corda.v5.ledger.utxo.Contract;
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction;
import org.jetbrains.annotations.NotNull;

import static java.util.stream.Collectors.toList;

public class BilateralContract implements Contract {

    public static class Propose implements Command { }
    public static class Update implements Command { }
    public static class Settle implements Command { }


    @Override
    public void verify(@NotNull UtxoLedgerTransaction transaction) {
        requireThat( transaction.getCommands().size() == 1, "Require a single command.");
        Command command = transaction.getCommands().get(0);

        if(command.getClass() == BilateralContract.Propose.class) {
            BilateralState output = transaction.getOutputStates(BilateralState.class).get(0);
            requireThat(output.getShrQty() >= 0, "When Propose, Share quantity must be positive or 0 for free trades");
            requireThat(output.getSettlementAmt() >= 0, "When Propose, Settlement amount must be positive or 0");
            requireThat(transaction.getInputContractStates().size() == 0, "When command is Propose there should be zero input state.");
            requireThat(transaction.getOutputContractStates().size() == 1, "When command is Propose there should be one and only one output state.");
        }
        else if(command.getClass() == BilateralContract.Update.class) {
            requireThat(transaction.getOutputContractStates().size() == 1, "When command is Update there should be one and only one output state.");
        }else if(command.getClass() == BilateralContract.Settle.class) {

            requireThat(transaction.getOutputContractStates().size() == 2, "When command is Update there should be one and only one output state.");

            BilateralState outputBilateralState = transaction.getOutputStates(BilateralState.class).get(0);
            SecurityToken outputSecurityToken = transaction.getOutputStates(SecurityToken.class).get(0);
            requireThat(outputSecurityToken.getCusip().equals(outputBilateralState.getSecurityID()), "SecurityToken cusip must match securityId");
            requireThat(outputSecurityToken.getQuantity().equals(outputBilateralState.getShrQty()), "SecurityToken quantity must match shrQty");

            SecurityToken inputSecurityToken = (SecurityToken) transaction.getInputContractStates().stream().filter(it -> it.getClass().equals(SecurityToken.class)).collect(toList()).get(0);
            requireThat(inputSecurityToken.getHolder().equals(outputBilateralState.getDeliver()), "deliverer and input token holder must be same party");

            BilateralState inputBilateralState= (BilateralState) transaction.getInputContractStates().stream().filter(it -> it.getClass().equals(BilateralState.class)).collect(toList()).get(0);
            requireThat(inputBilateralState.getReceiver().equals(outputSecurityToken.getHolder()), "receiver and output token holder must be same party");

        }
        else {
            throw new CordaRuntimeException("Unsupported command");
        }

    }
    private void requireThat(boolean asserted, String errorMessage) {
        if(!asserted) {
            throw new CordaRuntimeException("Failed requirement: " + errorMessage);
        }
    }

}
