package com.r3.corda.testing.smoketests.ion.workflows;

import com.r3.corda.testing.smoketests.ion.contracts.BilateralContract;
import com.r3.corda.testing.smoketests.ion.states.BilateralState;
import com.r3.corda.testing.smoketests.ion.states.SecurityToken;
import net.corda.v5.application.flows.ClientRequestBody;
import net.corda.v5.application.flows.ClientStartableFlow;
import net.corda.v5.application.flows.CordaInject;
import net.corda.v5.application.flows.FlowEngine;
import net.corda.v5.application.marshalling.JsonMarshallingService;
import net.corda.v5.application.membership.MemberLookup;
import net.corda.v5.base.annotations.Suspendable;
import net.corda.v5.base.exceptions.CordaRuntimeException;
import net.corda.v5.ledger.common.NotaryLookup;
import net.corda.v5.ledger.utxo.StateAndRef;
import net.corda.v5.ledger.utxo.UtxoLedgerService;
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction;
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder;
import net.corda.v5.membership.MemberInfo;
import net.corda.v5.membership.NotaryInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
//called by deliverer
public class SettleBilateralFlow implements ClientStartableFlow {

    private final static Logger log = LoggerFactory.getLogger(SettleBilateralFlow.class);

    @CordaInject
    public JsonMarshallingService jsonMarshallingService;

    @CordaInject
    public MemberLookup memberLookup;

    // Injects the UtxoLedgerService to enable the flow to make use of the Ledger API.
    @CordaInject
    public UtxoLedgerService ledgerService;

    @CordaInject
    public NotaryLookup notaryLookup;

    // FlowEngine service is required to run SubFlows.
    @CordaInject
    public FlowEngine flowEngine;


    @Suspendable
    @Override
    public String call(ClientRequestBody requestBody) {

        log.info("SettleBilateralFlow.call() called");

        try {
            // Obtain the deserialized input arguments to the flow from the requestBody.
            BilateralArgs flowArgs = requestBody.getRequestBodyAs(jsonMarshallingService, BilateralArgs.class);

            UUID bStateId = flowArgs.getLinearId();
            List<StateAndRef<BilateralState>> bilateralStateAndRefs = ledgerService.findUnconsumedStatesByType(BilateralState.class);
            List<StateAndRef<BilateralState>> bilateralStateAndRefsWithId = bilateralStateAndRefs.stream()
                    .filter(sar -> sar.getState().getContractState().getLinearId().equals(bStateId)).collect(toList());

            if (bilateralStateAndRefsWithId.size() != 1) throw new CordaRuntimeException("Multiple or zero bilateralState states with id " + bStateId + " found");
            StateAndRef<BilateralState> inputbilateralStateStateAndRef = bilateralStateAndRefsWithId.get(0);
            BilateralState inputBilateralState = inputbilateralStateStateAndRef.getState().getContractState();

            // Get MemberInfos for the Vnode running the flow and the otherMember.
            MemberInfo dtcc = requireNonNull(memberLookup.lookup(inputBilateralState.getDtcc()),"MemberLookup can't find otherMember specified in flow arguments.");
            MemberInfo deliver = memberLookup.myInfo();
            MemberInfo receiver = requireNonNull(memberLookup.lookup(inputBilateralState.getReceiver()),"MemberLookup can't find otherMember specified in flow arguments.");
            MemberInfo dtccObserver = requireNonNull(memberLookup.lookup(inputBilateralState.getDtccObserver()),"MemberLookup can't find otherMember specified in flow arguments.");


            String securityId = inputBilateralState.getSecurityID();
            List<StateAndRef<SecurityToken>> SecurityTokenStateAndRefs = ledgerService.findUnconsumedStatesByType(SecurityToken.class);
            List<StateAndRef<SecurityToken>> SecurityTokenStateAndRefsWithId = SecurityTokenStateAndRefs.stream()
                    .filter(sar -> sar.getState().getContractState().getCusip().equals(securityId)).collect(toList());
            if (SecurityTokenStateAndRefsWithId.size() != 1) throw new CordaRuntimeException("Multiple or zero securityToken states with id " + bStateId + " found");
            StateAndRef<SecurityToken> inputSecurityTokenStateAndRef = SecurityTokenStateAndRefsWithId.get(0);
            SecurityToken inputSecurityToken = inputSecurityTokenStateAndRef.getState().getContractState();

            SecurityToken outputSecurityToken = inputSecurityToken.transfer(receiver.getName(), Arrays.asList(receiver.getLedgerKeys().get(0)));

            // Create the BilateralState from the input arguments and member information.
            BilateralState outputBilateralState = inputBilateralState.update(flowArgs.getTradeStatus(), flowArgs.getShrQty(), flowArgs.getSecurityID(),
                    flowArgs.getSettlementAmt(), flowArgs.getSetlmntCrncyCd(), flowArgs.getSttlmentlnsDlvrrRefId());

            // Obtain the Notary name and public key.
            NotaryInfo notary = notaryLookup.getNotaryServices().iterator().next();

           /* Not required in Gecko RC02
            PublicKey notaryKey = null;

            for(MemberInfo memberInfo: memberLookup.lookup()){
                if(Objects.equals(
                        memberInfo.getMemberProvidedContext().get("corda.notary.service.name"),
                        notary.getName().toString())) {
                    notaryKey = memberInfo.getLedgerKeys().get(0);
                    break;
                }
            }
            // Note, in Java CorDapps only unchecked RuntimeExceptions can be thrown not
            // declared checked exceptions as this changes the method signature and breaks override.
            if(notaryKey == null) {
                throw new CordaRuntimeException("No notary PublicKey found");
            }
            */
            // Use UTXOTransactionBuilder to build up the draft transaction.
            UtxoTransactionBuilder txBuilder = ledgerService.createTransactionBuilder()
                    .setNotary(notary.getName())
                    .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                    .addInputState(inputbilateralStateStateAndRef.getRef())
                    .addInputState(inputSecurityTokenStateAndRef.getRef())
                    .addOutputState(outputBilateralState)
                    .addOutputState(outputSecurityToken)
                    .addCommand(new BilateralContract.Settle())
                    .addSignatories(outputBilateralState.getParticipants());

            // Convert the transaction builder to a UTXOSignedTransaction and sign with this Vnode's first Ledger key.
            // Note, toSignedTransaction() is currently a placeholder method, hence being marked as deprecated.
            @SuppressWarnings("DEPRECATION")
            UtxoSignedTransaction signedTransaction = txBuilder.toSignedTransaction();

            // Call FinalizeBilateral which will finalise the transaction.
            // If successful the flow will return a String of the created transaction id,
            // if not successful it will return an error message.
            return flowEngine.subFlow(new FinalizeTxFlow.FinalizeTx(signedTransaction, Arrays.asList(deliver.getName(),receiver.getName(),dtcc.getName(),dtccObserver.getName())));
        }
        // Catch any exceptions, log them and rethrow the exception.
        catch (Exception e) {
            log.warn("Failed to process utxo flow for request body " + requestBody + " because: " + e.getMessage());
            throw new CordaRuntimeException(e.getMessage());
        }
    }
}

/*
RequestBody for triggering the flow via http-rpc:
{
    "clientRequestId": "settleBilateral-1",
    "flowClassName": "com.r3.developers.csdetemplate.ION.SettleBilateralFlow",
    "requestData": {
        "tradeStatus":"SETTLED",
        "shrQty":"25",
        "securityID":"USCA765248",
        "settlementAmt":"15",
        "deliver":"CN=deliverer, OU=Test Dept, O=R3, L=London, C=GB",
        "receiver":"CN=receiver, OU=Test Dept, O=R3, L=London, C=GB",
        "settlementDelivererId":"delivererIdXXXX",
        "settlementReceiverID":"receiverIDXXXX",
        "dtcc":"CN=dtcc, OU=Test Dept, O=R3, L=NewYork, C=US",
        "dtccObserver":"CN=dtccObserver, OU=Test Dept, O=R3, L=Washington, C=US",
        "setlmntCrncyCd":"WhateverThisIs",
        "sttlmentlnsDlvrrRefId":"WhateverThisIsToo",
        "linearId":"22036b21-746d-42d5-ac35-b86706b45b54"
        }
}

{
    "clientRequestId": "list-2",
    "flowClassName": "com.r3.developers.csdetemplate.ION.QueryAllStates",
    "requestData": {}
}
 */