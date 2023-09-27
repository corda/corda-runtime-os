//package com.r3.corda.atomic.swap.workflows
//
//import net.corda.core.crypto.CompositeKey
//import net.corda.core.crypto.TransactionSignature
//import net.corda.core.flows.FlowLogic
//import net.corda.core.flows.FlowSession
//import net.corda.core.flows.InitiatedBy
//import net.corda.core.flows.InitiatingFlow
//import net.corda.core.identity.Party
//import net.corda.core.transactions.SignedTransaction
//import net.corda.core.utilities.unwrap
//import net.corda.v5.application.flows.ClientRequestBody
//import net.corda.v5.application.flows.ClientStartableFlow
//import net.corda.v5.application.flows.CordaInject
//import net.corda.v5.application.marshalling.JsonMarshallingService
//import java.security.PublicKey
//
///**
// * Collect signatures for the provided [SignedTransaction], from the list of [Party] provided.
// * This is an initiating flow, and is used where some of the required signatures are from
// * [CompositeKey]s. The standard Corda CollectSignaturesFlow will not work in this case.
// * @param stx - the [SignedTransaction] to sign
// * @param signers - the list of signing [Party]s
// */
//
//data class CollectSignaturesForCompositeKeyFlowArgs(val stx: String, val signers: List<PublicKey>)
//class CollectSignaturesForCompositeKeyFlow: ClientStartableFlow {
//    @CordaInject
//    lateinit var jsonMarshallingService: JsonMarshallingService
//
//    override fun call(requestBody: ClientRequestBody): String {
//        val flowArgs = requestBody.getRequestBodyAs(jsonMarshallingService, CollectSignaturesForCompositeKeyFlowArgs::class.java)
//        val sessions = flowArgs.signers.map { initiateFlow(it) }
//
//        // We filter out any responses that are not
//        // `TransactionSignature`s (i.e. refusals to sign).
//        val signatures = sessions
//            .map { it.sendAndReceive<Any>(flowArgs.stx).unwrap { data -> data } }
//            .filterIsInstance<TransactionSignature>()
//        return flowArgs.stx.withAdditionalSignatures(signatures)
//    }
//}
//
//
////@InitiatedBy(CollectSignaturesForCompositeKeyFlow::class)
////class CollectSignaturesForCompositesResponder(private val otherPartySession: FlowSession) : FlowLogic<Unit>() {
////
////    @Suspendable
////    override fun call() {
////        otherPartySession.receive<SignedTransaction>().unwrap { partStx ->
////            // TODO: add conditions where we might not sign
////
////            val returnStatus = serviceHub.createSignature(partStx)
////            otherPartySession.send(returnStatus)
////        }
////    }
////}