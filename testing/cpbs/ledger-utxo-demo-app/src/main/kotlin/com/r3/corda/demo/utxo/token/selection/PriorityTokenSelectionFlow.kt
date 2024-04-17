package com.r3.corda.demo.utxo.token.selection

import com.r3.corda.demo.utxo.contract.TOKEN_AMOUNT
import com.r3.corda.demo.utxo.contract.TOKEN_ISSUER_HASH
import com.r3.corda.demo.utxo.contract.TOKEN_SYMBOL
import com.r3.corda.demo.utxo.contract.TOKEN_TYPE
import com.r3.corda.demo.utxo.contract.TestCommand
import com.r3.corda.demo.utxo.contract.TestUtxoState
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.token.selection.ClaimedToken
import net.corda.v5.ledger.utxo.token.selection.Strategy
import net.corda.v5.ledger.utxo.token.selection.TokenClaimCriteria
import net.corda.v5.ledger.utxo.token.selection.TokenSelection
import net.corda.v5.membership.MemberInfo
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

private const val PRIORITY_TOKEN_SELECTION_PROTOCOL = "token-selection-priority-flow-protocol"

@Suppress("unused")
@InitiatingFlow(protocol = PRIORITY_TOKEN_SELECTION_PROTOCOL)
class PriorityTokenSelectionFlow : ClientStartableFlow {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var tokenSelection: TokenSelection

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var digestService: DigestService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @CordaInject
    lateinit var marshallingService: JsonMarshallingService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("PriorityTokenSelectionFlow starting...")
        try {
            val request =
                requestBody.getRequestBodyAs(marshallingService, PriorityTokenSelectionMsg::class.java)

            val claimedTokenList = claimTokens(request.noTokensToClaim)

            if (claimedTokenList.size != request.noTokensToClaim) {
                log.warn("Failed to claim enough tokens. Claimed tokens: $${claimedTokenList.size}" +
                        "No. Tokens to claim: ${request.noTokensToClaim}")
                return "Failed to claim enough tokens"
            }


            val memberInfo = requireNotNull(memberLookup.lookup(MemberX500Name.parse(request.memberX500))) {
                "Member ${request.memberX500} does not exist in the membership group"
            }

            // Spend the tokens so they cannot be selected again
            spendTokens(claimedTokenList, memberInfo)

            // Create a list with the priority of each claimed token
            // val tokenPriorityList = mutableListOf<Long?>()
            val tokenPriorityList = claimedTokenList.map {
                utxoLedgerService.resolve<TestUtxoState>(it.stateRef).state.contractState.priority
            }

            return jsonMarshallingService.format(tokenPriorityList)
        } catch (e: Exception) {
            log.error("Unexpected error while processing the flow", e)
            throw e
        }
    }

    @Suspendable
    private fun claimTokens(noTokensToClaim: Int): List<ClaimedToken> {

        val queryCriteria = TokenClaimCriteria(
            TOKEN_TYPE,
            digestService.parseSecureHash(TOKEN_ISSUER_HASH),
            notaryLookup.notaryServices.single().name,
            TOKEN_SYMBOL,
            TOKEN_AMOUNT,
            Strategy.PRIORITY
        )

        val claimedTokenList = mutableListOf<ClaimedToken>()
        do {
            val tokenClaim = tokenSelection.tryClaim("claim-${UUID.randomUUID()}", queryCriteria)
            if (tokenClaim != null) {
                // Lookup the states that match the returned tokens
                claimedTokenList.add(tokenClaim.claimedTokens.single())
            }
        } while (tokenClaim != null && claimedTokenList.size < noTokensToClaim)

        return claimedTokenList
    }

    @Suspendable
    private fun spendTokens(claimedTokenList: List<ClaimedToken>, memberInfo: MemberInfo) {
        val tokensToBeSpent = claimedTokenList.map { it.stateRef }

        val me = memberLookup.myInfo()

        val participants = listOf(me.ledgerKeys.first(), memberInfo.ledgerKeys.first())

        log.info("Creating transaction...")
        val txBuilder = utxoLedgerService.createTransactionBuilder()

        val now = Instant.now()

        val signedTransaction = txBuilder
            .setNotary(notaryLookup.notaryServices.single().name)
            .setTimeWindowBetween(now, now.plus(1, ChronoUnit.DAYS))
            .addInputStates(tokensToBeSpent)
            .addCommand(TestCommand())
            .addSignatories(participants)
            .toSignedTransaction()

        val session = flowMessaging.initiateFlow(memberInfo.name)

        try {
            val finalizedSignedTransaction = utxoLedgerService.finalize(
                signedTransaction,
                listOf(session)
            )
            log.info("Created and finalised transaction with id='${finalizedSignedTransaction.transaction.id}'")
        } catch (e: Exception) {
            log.warn("Finality failed", e)
            "Finality failed, ${e.message}"
        }
    }

    private data class PriorityTokenSelectionMsg(
        val noTokensToClaim: Int,
        val memberX500: String
    )

}

@Suppress("unused")
@InitiatedBy(protocol = PRIORITY_TOKEN_SELECTION_PROTOCOL)
class SpendTokenResponder : ResponderFlow {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @Suspendable
    override fun call(session: FlowSession) {
        try {
            val finalizedSignedTransaction = utxoLedgerService.receiveFinality(session) {
                log.info("Verified the transaction- ${it.id}")
            }
            log.info("Finished responder flow - ${finalizedSignedTransaction.transaction.id}")
        } catch (e: Exception) {
            log.warn("Exceptionally finished spend token responder flow", e)
        }
    }
}
