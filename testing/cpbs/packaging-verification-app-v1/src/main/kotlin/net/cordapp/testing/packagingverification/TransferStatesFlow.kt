package net.cordapp.testing.packagingverification

import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.token.selection.TokenClaim
import net.corda.v5.ledger.utxo.token.selection.TokenClaimCriteria
import net.corda.v5.ledger.utxo.token.selection.TokenSelection
import net.cordapp.testing.packagingverification.contract.ISSUER
import net.cordapp.testing.packagingverification.contract.STATE_NAME
import net.cordapp.testing.packagingverification.contract.STATE_SYMBOL
import net.cordapp.testing.packagingverification.contract.SimpleCommand
import net.cordapp.testing.packagingverification.contract.SimpleState
import net.cordapp.testing.packagingverification.contract.toSecureHash
import org.slf4j.LoggerFactory
import java.lang.IllegalArgumentException
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

@InitiatingFlow(protocol = "net.cordapp.testing.packagingverification.TransferStatesFlow")
class TransferStatesFlow : ClientStartableFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @CordaInject
    lateinit var tokenSelection: TokenSelection

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        val transferRequest = requestBody.getRequestBodyAs(jsonMarshallingService, TransferRequest::class.java)
        log.info("Transferring States of value ${transferRequest.value}")

        val recipientX500 = MemberX500Name.parse(transferRequest.recipientX500Name)
        val counterpartyMember = memberLookup.lookup(recipientX500)
            ?: throw IllegalArgumentException("Member info cannot be found for $recipientX500")

        val notary = notaryLookup.notaryServices.single()

        val selectionCriteria = TokenClaimCriteria(
            STATE_NAME,
            ISSUER.toSecureHash(),
            notary.name,
            STATE_SYMBOL,
            BigDecimal(transferRequest.value)
        )

        var tokenClaim: TokenClaim? = null
        try {
            log.info("Making token claim")
            tokenClaim = tokenSelection.tryClaim(selectionCriteria) ?: throw CordaRuntimeException("Cannot claim tokens.")
            log.info("Got token claim, ${tokenClaim.claimedTokens.size} tokens")

            val myInfo = memberLookup.myInfo()
            val myPublicKey = myInfo.ledgerKeys.first()

            val totalAmount = tokenClaim.claimedTokens.fold(0L) { acc, claimedToken ->
                acc + claimedToken.amount.toLong()
            }
            val change = totalAmount - transferRequest.value
            val outputStates = mutableListOf<SimpleState>()
            if (change < 0) {
                throw CordaRuntimeException("Total amount of $totalAmount cannot meet request to transfer ${transferRequest.value}")
            }

            if (change != 0L) {
                outputStates += SimpleState(change, listOf(myPublicKey))
            }

            outputStates += SimpleState(transferRequest.value, listOf(counterpartyMember.ledgerKeys.first()))

            log.info("Creating transaction")
            val signedTransaction = utxoLedgerService.transactionBuilder.setNotary(Party(notary.name, notary.publicKey))
                .addInputStates(tokenClaim.claimedTokens.map { it.stateRef })
                .addOutputStates(outputStates)
                .addSignatories(listOf(counterpartyMember.ledgerKeys.first(), myPublicKey))
                .setTimeWindowUntil(Instant.now() + Duration.ofDays(1))
                .addCommand(SimpleCommand())
                .toSignedTransaction()

            log.info("Initiating recipient Flow")
            val session = flowMessaging.initiateFlow(recipientX500)
            log.info("Finalizing transaction")
            utxoLedgerService.finalize(signedTransaction, listOf(session))
            log.info("Finalized transaction")
        } catch (ex: Exception) {
            log.info("TransferStatesFlow failed", ex)
        }
        finally {
            // Release the claim on the tokens' states
            tokenClaim?.useAndRelease(tokenClaim.claimedTokens.map { it.stateRef })
        }

        log.info("Finished transferring States")
        return ""
    }
}