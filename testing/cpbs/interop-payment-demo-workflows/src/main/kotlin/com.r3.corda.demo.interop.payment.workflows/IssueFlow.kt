package com.r3.corda.demo.interop.payment.workflows

import com.r3.corda.demo.interop.payment.contracts.PaymentContract
import com.r3.corda.demo.interop.payment.states.PaymentState
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.UUID


data class IssueFlowArgs(val amount: String)

class IssueFlow : ClientStartableFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("IssueFlow.call() called")

        try {
            val flowArgs = requestBody.getRequestBodyAs(jsonMarshallingService, IssueFlowArgs::class.java)

            val myInfo = memberLookup.myInfo()

            val iou = PaymentState(
                amount = flowArgs.amount.toInt(),
                issuer = myInfo.name,
                owner = myInfo.name,
                linearId = UUID.randomUUID(),
                participants = listOf(myInfo.ledgerKeys[0])
            )

            val notary = notaryLookup.lookup(MemberX500Name.parse("CN=NotaryService, OU=Test Dept, O=R3, L=London, C=GB"))
                ?: throw CordaRuntimeException("NotaryLookup can't find notary specified in flow arguments.")

            val txBuilder = ledgerService.createTransactionBuilder()
                .setNotary(notary.name)
                .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                .addOutputState(iou)
                .addCommand(PaymentContract.Issue())
                .addSignatories(iou.participants)

            val signedTransaction = txBuilder.toSignedTransaction()

            return flowEngine.subFlow(FinalizeFlow(signedTransaction, listOf()))

        } catch (e: Exception) {
            log.warn("Failed to process utxo flow for request body '$requestBody' because: '${e.message}'")
            throw e
        }
    }
}
