package net.cordapp.utxo.apples.flows.issue

import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.cordapp.utxo.apples.states.AppleStamp
import net.cordapp.utxo.apples.contracts.AppleStampContract
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@InitiatingFlow(protocol = "create-and-issue-apple-stamp")
class CreateAndIssueAppleStampFlow : ClientStartableFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        val request = requestBody.getRequestBodyAs(jsonMarshallingService, CreateAndIssueAppleStampRequest::class.java)
        val stampDescription = request.stampDescription
        val holderName = request.holder

        // Retrieve the notaries public key (this will change)
        val notaryInfo = notaryLookup.notaryServices.single()
        val notaryKey = memberLookup.lookup().single {
            it.memberProvidedContext["corda.notary.service.name"] == notaryInfo.name.toString()
        }.ledgerKeys.first()
        val notary = Party(notaryInfo.name, notaryKey)

        val issuer = memberLookup.myInfo().let { Party(it.name, it.ledgerKeys.first()) }
        val holder = memberLookup.lookup(holderName)
            ?.let { Party(it.name, it.ledgerKeys.first()) }
            ?: throw IllegalArgumentException("The holder $holderName does not exist within the network")

        // Building the output AppleStamp state
        val newStamp = AppleStamp(
            id = UUID.randomUUID(),
            stampDesc = stampDescription,
            issuer = issuer,
            holder = holder,
            participants = listOf(issuer.owningKey, holder.owningKey)
        )

        // Create the transaction
        @Suppress("DEPRECATION")
        val transaction = utxoLedgerService.getTransactionBuilder()
            .setNotary(notary)
            .addOutputState(newStamp)
            .addCommand(AppleStampContract.Commands.Issue())
            .setTimeWindowUntil(Instant.now().plus(1, ChronoUnit.DAYS))
            .addSignatories(listOf(issuer.owningKey, holder.owningKey))
            .toSignedTransaction()

        val session = flowMessaging.initiateFlow(holderName)

        return try {
            // Send the transaction and state to the counterparty and let them sign it
            // Then notarise and record the transaction in both parties' vaults.
            utxoLedgerService.finalize(transaction, listOf(session))
            log.info("Flow Finished ID = ${newStamp.id}")
            newStamp.id.toString()
        } catch (e: Exception) {
            log.warn("Flow failed", e)
            "Flow failed, message: ${e.message}"
        }
    }
}