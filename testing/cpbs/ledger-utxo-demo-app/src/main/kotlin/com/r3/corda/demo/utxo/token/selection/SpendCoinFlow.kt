package com.r3.corda.demo.utxo.token.selection

import com.r3.corda.demo.utxo.contract.CoinState
import com.r3.corda.demo.utxo.contract.TestCommand
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.token.selection.TokenClaimCriteria
import net.corda.v5.ledger.utxo.token.selection.TokenSelection
import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.TimeUnit
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory

@InitiatingFlow(protocol = "utxo-spend-coin-protocol")
class SpendCoinFlow : ClientStartableFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }


    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @CordaInject
    lateinit var tokenSelection: TokenSelection

    @CordaInject
    lateinit var digestService: DigestService

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("Starting Coin Spend...")
        try {
            val spendRequest = requestBody.getRequestBodyAs(jsonMarshallingService, SpendCoinMessage::class.java)
//            val bankX500 = MemberX500Name.parse(spendRequest.issuerBankX500)
            val notary = notaryLookup.notaryServices.single()

            val selectionCriteria = TokenClaimCriteria(
                CoinState.tokenType,
                digestService.parseSecureHash("SHA-256:54111C3F78233454D7F53AE7748F47298810B28F75FA652E42AA3FAA2E80049F"),
                notary.name,
                spendRequest.currency,
                BigDecimal(spendRequest.targetAmount)
            )

            val tokenClaim = tokenSelection.tryClaim(selectionCriteria)

            if (tokenClaim == null) {
                log.info("No tokens found for '${jsonMarshallingService.format(selectionCriteria)}'")
                return jsonMarshallingService.format(SpendCoinResponseMessage(listOf(), listOf(), listOf()))
            }

            log.info(
                "Found ${tokenClaim.claimedTokens.size} tokens found for '${
                    jsonMarshallingService.format(
                        selectionCriteria
                    )
                }'"
            )

            val spentCoins = tokenClaim.claimedTokens.take(spendRequest.maxCoinsToUse).map { it.stateRef }

//            val notaryKey = memberLookup.lookup().single {
//                it.memberProvidedContext["corda.notary.service.name"] == notary.commonName
//            }.ledgerKeys.first()

//            val me = memberLookup.myInfo()
//            val spendCoinMessage = requestBody.getRequestBodyAs(jsonMarshallingService, SpendCoinMessage::class.java)
//            val bankX500 = MemberX500Name.parse(spendCoinMessage.issuerBankX500)
//
//            val bank = requireNotNull(memberLookup.lookup(bankX500)) {
//                "Member $bankX500 does not exist in the membership group"
//            }
//
//            val participants = listOf(me.ledgerKeys.first(), bank.ledgerKeys.first())
//
//            log.info("Creating transaction...")
//            val txBuilder = utxoLedgerService.createTransactionBuilder()

//            @Suppress("DEPRECATION")
//            val signedTransaction = txBuilder
//                .setNotary(notary.name)
//                .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(TimeUnit.DAYS.toMillis(1)))
//                .addInputStates(spentCoins)
//                .addCommand(TestCommand())
//                .addSignatories(participants)
//                .toSignedTransaction()
//
//            val bankSession = flowMessaging.initiateFlow(bank.name)
//
//            val ret = try {
//                val finalizedSignedTransaction = utxoLedgerService.finalize(
//                    signedTransaction,
//                    listOf(bankSession)
//                )
//
//                log.info("Created and finalised transaction with id='${finalizedSignedTransaction.transaction.id}'")
//                finalizedSignedTransaction.transaction.id.toString()
//            } catch (e: Exception) {
//                log.warn("Finality failed", e)
//                "Finality failed, ${e.message}"
//            }
//
//            log.info("Return value: $ret")
//
//            val coinsToRelease =
//                tokenClaim.claimedTokens.drop(spendRequest.maxCoinsToUse).map { it.stateRef.toString() }

            val response = SpendCoinResponseMessage(
                foundCoins = tokenClaim.claimedTokens,
                spentCoins = spentCoins.map { it.toString() },
                releasedCoins = emptyList()//coinsToRelease
            )

//            log.info("Releasing claim...")
//            tokenClaim.useAndRelease(spentCoins)
//            log.info("Claim released.")

            return jsonMarshallingService.format(response)
        } catch (e: Exception) {
            log.warn("Failed to process utxo flow for request body '$requestBody' because:'${e.message}'")
            throw e
        }
    }
}
