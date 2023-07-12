package com.r3.corda.demo.interop.tokens.workflows

import com.r3.corda.demo.interop.tokens.contracts.TokenContract
import com.r3.corda.demo.interop.tokens.states.TokenState
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.UUID


class IssueSubFlow(private val params: IssueFlowArgs) : SubFlow<IssueFlowResult> {

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
    override fun call(): IssueFlowResult {
        log.info("IssueFlow.call() called")

        try {
            val flowArgs = params

            val myInfo = memberLookup.myInfo()

            val outputState = TokenState(
                amount = flowArgs.amount.toInt(),
                issuer = myInfo.name,
                owner = myInfo.name,
                linearId = UUID.randomUUID(),
                participants = listOf(myInfo.ledgerKeys[0])
            )

            val notaries = notaryLookup.notaryServices
            require(notaries.isNotEmpty()) { "No notaries are available." }
            require(notaries.size == 1) { "Too many notaries $notaries." }
            val notary = notaryLookup.notaryServices.single()

            val txBuilder = ledgerService.createTransactionBuilder()
                .setNotary(notary.name)
                .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                .addOutputState(outputState)
                .addCommand(TokenContract.Issue())
                .addSignatories(outputState.participants)

            val signedTransaction = txBuilder.toSignedTransaction()

            val transactionId = flowEngine.subFlow(FinalizeFlow(signedTransaction, listOf()))

            return IssueFlowResult(transactionId, outputState.linearId.toString())

        } catch (e: Exception) {
            log.warn("Failed to process utxo sub flow because: '${e.message}'")
            throw e
        }
    }
}
