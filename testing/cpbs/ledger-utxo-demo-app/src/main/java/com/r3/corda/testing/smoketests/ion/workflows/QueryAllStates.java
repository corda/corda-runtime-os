package com.r3.corda.testing.smoketests.ion.workflows;

import com.r3.corda.testing.smoketests.ion.states.BilateralState;
import com.r3.corda.testing.smoketests.ion.states.SecurityToken;
import net.corda.v5.application.flows.*;
import net.corda.v5.application.marshalling.JsonMarshallingService;
import net.corda.v5.base.annotations.Suspendable;
import net.corda.v5.ledger.utxo.StateAndRef;
import net.corda.v5.ledger.utxo.UtxoLedgerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public abstract class QueryAllStates implements ClientStartableFlow {

    private final static Logger log = LoggerFactory.getLogger(QueryAllStates.class);

    @CordaInject
    public JsonMarshallingService jsonMarshallingService;

    // Injects the UtxoLedgerService to enable the flow to make use of the Ledger API.
    @CordaInject
    public UtxoLedgerService utxoLedgerService;

    @Suspendable
    @Override
    public String call(ClientRequestBody requestBody) {

        log.info("QueryAllStates.call() called");

        // Queries the VNode's vault for unconsumed states and converts the result to a serializable DTO.
        List<StateAndRef<BilateralState>> states = utxoLedgerService.findUnconsumedStatesByType(BilateralState.class);
        List<BilateralArgs> results = states.stream().map(stateAndRef ->
                new BilateralArgs(
                        stateAndRef.getState().getContractState().getTradeStatus(),
                        stateAndRef.getState().getContractState().getShrQty(),
                        stateAndRef.getState().getContractState().getSecurityID(),
                        stateAndRef.getState().getContractState().getSettlementAmt(),
                        stateAndRef.getState().getContractState().getDeliver(),
                        stateAndRef.getState().getContractState().getReceiver(),
                        stateAndRef.getState().getContractState().getSettlementDelivererId(),
                        stateAndRef.getState().getContractState().getSettlementReceiverID(),
                        stateAndRef.getState().getContractState().getDtcc(),
                        stateAndRef.getState().getContractState().getDtccObserver(),
                        stateAndRef.getState().getContractState().getSetlmntCrncyCd(),
                        stateAndRef.getState().getContractState().getSttlmentlnsDlvrrRefId(),
                        stateAndRef.getState().getContractState().getLinearId()
                )
        ).collect(Collectors.toList());

        List<StateAndRef<SecurityToken>> statesSecurityToken = utxoLedgerService.findUnconsumedStatesByType(SecurityToken.class);
        List<SelfCreateSecurityTokenFlowArgs> resultsSecurityToken = statesSecurityToken.stream().map(stateAndRef ->
                new SelfCreateSecurityTokenFlowArgs(
                        stateAndRef.getState().getContractState().getCusip(),
                        stateAndRef.getState().getContractState().getQuantity(),
                        stateAndRef.getState().getContractState().getHolder(),
                        stateAndRef.getState().getContractState().getTokenForger()
                )
        ).collect(Collectors.toList());



        // Uses the JsonMarshallingService's format() function to serialize the DTO to Json.
        return jsonMarshallingService.format(Arrays.asList(results,resultsSecurityToken));
    }
}