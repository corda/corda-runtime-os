package com.r3.corda.testing.smoketests.ion.workflows;

import com.r3.corda.testing.smoketests.ion.contracts.BilateralContract;
import com.r3.corda.testing.smoketests.ion.states.BilateralState;
import net.corda.v5.application.flows.ClientRequestBody;
import net.corda.v5.application.flows.ClientStartableFlow;
import net.corda.v5.application.flows.CordaInject;
import net.corda.v5.application.flows.FlowEngine;
import net.corda.v5.application.marshalling.JsonMarshallingService;
import net.corda.v5.application.membership.MemberLookup;
import net.corda.v5.base.annotations.Suspendable;
import net.corda.v5.ledger.common.NotaryLookup;
import net.corda.v5.ledger.utxo.UtxoLedgerService;
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder;
import net.corda.v5.membership.MemberInfo;
import net.corda.v5.membership.NotaryInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

public class CreateBilateralFlow implements ClientStartableFlow {

    private final static Logger log = LoggerFactory.getLogger(CreateBilateralFlow.class);

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

        log.info("CreateBilateralFlow.call() called");

        try {
            var memList = memberLookup.lookup();
            memList.stream().forEach(e -> log.info("MemberInfo is: "+e.getName()));
            // Obtain the deserialized input arguments to the flow from the requestBody.
            BilateralArgs flowArgs = requestBody.getRequestBodyAs(jsonMarshallingService, BilateralArgs.class);

            // Get MemberInfos for the Vnode running the flow and the otherMember.
            MemberInfo deliver = requireNonNull(memberLookup.lookup(flowArgs.getDeliver()),"MemberLookup can't find otherMember specified in flow arguments.");
            MemberInfo receiver = requireNonNull(memberLookup.lookup(flowArgs.getReceiver()),"MemberLookup can't find otherMember specified in flow arguments.");
            MemberInfo dtcc = memberLookup.myInfo();//DTCC is the bilateral initiator
            MemberInfo dtccObserver = requireNonNull(memberLookup.lookup(flowArgs.getDtccObserver()),"MemberLookup can't find otherMember specified in flow arguments.");

            // Create the BilateralState from the input arguments and member information.
            BilateralState bilateralState = new BilateralState(
                    "PROPOSED",
                    flowArgs.getShrQty(),
                    flowArgs.getSecurityID(),
                    flowArgs.getSettlementAmt(),
                    flowArgs.getDeliver(),
                    flowArgs.getReceiver(),
                    flowArgs.getSettlementDelivererId(),
                    flowArgs.getSettlementReceiverID(),
                    dtcc.getName(), //DTCC is the bilateral initiator
                    flowArgs.getDtccObserver(),
                    flowArgs.getSetlmntCrncyCd(),
                    flowArgs.getSttlmentlnsDlvrrRefId(),
                    UUID.randomUUID(),
                    Arrays.asList(deliver.getLedgerKeys().get(0),receiver.getLedgerKeys().get(0),dtcc.getLedgerKeys().get(0),dtccObserver.getLedgerKeys().get(0))
            );

            // Obtain the Notary name and public key.
            NotaryInfo notary = notaryLookup.getNotaryServices().iterator().next();
            log.info("Notary info:");
            log.info(notary.getName().toString());

            /* Not required in Gecko RC02
            PublicKey notaryKey = null;
            log.info("before for");
            for(MemberInfo memberInfo: memberLookup.lookup()){
                if(Objects.equals(
                        memberInfo.getMemberProvidedContext().get("corda.notary.service.name"),
                        notary.getName().toString())) {
                    notaryKey = memberInfo.getLedgerKeys().get(0);
                    log.info("in if");
                    log.info(notaryKey.toString());
                    break;
                }
                else log.info("Notary key not created");
            }
            log.info("check after for");
            log.info("Notary key: "+notaryKey.toString());
            // Note, in Java CorDapps only unchecked RuntimeExceptions can be thrown not
            // declared checked exceptions as this changes the method signature and breaks override.
            if(notaryKey == null) {
                throw new CordaRuntimeException("No notary PublicKey found");
            }
            */

            // Use UTXOTransactionBuilder to build up the draft transaction.

            log.info("CREATING TRANSACTION BUILDER");

            UtxoTransactionBuilder txBuilder = ledgerService.createTransactionBuilder()
                    .setNotary(notary.getName())
                    .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                    .addOutputState(bilateralState)
                    .addCommand(new BilateralContract.Propose())
                    .addSignatories(Set.copyOf(bilateralState.getParticipants()));

            log.info("Calling toSignedTransaction method.");
            // Convert the transaction builder to a UTXOSignedTransaction and sign with this Vnode's first Ledger key.
            // Note, toSignedTransaction() is currently a placeholder method, hence being marked as deprecated.
            @SuppressWarnings("DEPRECATION")
            var signedTransaction = txBuilder.toSignedTransaction();

            log.info("signedTransaction metaData in json format" + jsonMarshallingService.format(signedTransaction.getMetadata()));

            // Call FinalizeBilateral which will finalise the transaction.
            // If successful the flow will return a String of the created transaction id,
            // if not successful it will return an error message.
            return flowEngine.subFlow(new FinalizeTxFlow.FinalizeTx(signedTransaction, Arrays.asList(deliver.getName(), receiver.getName(), dtccObserver.getName())));
        }
        // Catch any exceptions, log them and rethrow the exception.
        catch (Exception e) {
            log.warn("Failed to process utxo flow for request body " + requestBody + " because: " + e.getMessage());
            throw e;
        }
    }
}
/*
RequestBody for triggering the flow via http-rpc:
{
    "clientRequestId": "createBilateral-1",
    "flowClassName": "com.r3.developers.csdetemplate.ION.CreateBilateralFlow",
    "requestData": {
        "tradeStatus":"Proposed",
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
        "linearId":"d5824f31-5785-4e44-acbf-c52784fc04e5"
        }
}
 */
/*
RequestBody for triggering the flow via http-rpc:
{
    "clientRequestId": "list-1",
    "flowClassName": "com.r3.developers.csdetemplate.ION.QueryAllStates",
    "requestData": {}
}
*/

