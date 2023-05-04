package com.r3.corda.demo.utxo.token

import java.math.BigDecimal
import java.time.Instant
import kotlin.time.Duration.Companion.days
import net.corda.crypto.core.parseSecureHash
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory

@InitiatingFlow(protocol = "utxo-coin-protocol")
class CreateCoinFlow : ClientStartableFlow {
    private companion object {
        val log = LoggerFactory.getLogger(CreateCoinFlow::class.java)
    }

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("Starting Coin Creation...")
        try {
            val creationRequest = requestBody.getRequestBodyAs(
                jsonMarshallingService,
                CreateCoinMessage::class.java
            )

            val me = memberLookup.myInfo()
            val bankX500 = MemberX500Name.parse(creationRequest.issuerBankX500)

            val bank = requireNotNull(memberLookup.lookup(bankX500)) {
                "Member $bankX500 does not exist in the membership group"
            }

            val participants = mutableListOf(me.ledgerKeys.first(), bank.ledgerKeys.first())

            log.info(
                "Creating ${creationRequest.numberOfCoins} coins issued to '${me.name}' " +
                        "from '${bank.name}' each with a value of '${creationRequest.valueOfCoin}'"
            )

            val ownerHash = if (creationRequest.ownerHash == null) {
                null
            } else {
                parseSecureHash(creationRequest.ownerHash)
            }

            val coins = IntRange(1, creationRequest.numberOfCoins).map {
                CoinState(
                    issuer = bankX500.toSecureHash(),
                    currency = creationRequest.currency,
                    value = BigDecimal(creationRequest.valueOfCoin),
                    participants = participants,
                    tag = creationRequest.tag,
                    ownerHash = ownerHash
                )
            }

            val notary = notaryLookup.notaryServices.first()
//            val notaryKey = memberLookup.lookup().single {
//                it.memberProvidedContext["corda.notary.service.name"] == notary.name.toString()
//            }.ledgerKeys.first()

            log.info("Creating transaction...")
            val txBuilder = utxoLedgerService.createTransactionBuilder()


            @Suppress("DEPRECATION")
            val signedTransaction = txBuilder
                .setNotary(notary.name)
                .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(1.days.inWholeMilliseconds))
                .addOutputStates(coins)
                .addCommand(NullCoinCommand())
                .addSignatories(participants)
                .toSignedTransaction()

            val bankSession = flowMessaging.initiateFlow(bank.name)

            return try {
                val finalizedSignedTransaction = utxoLedgerService.finalize(
                    signedTransaction,
                    listOf(bankSession)
                )

                log.info("Created and finalised transaction with id='${finalizedSignedTransaction.transaction.id}'")
                finalizedSignedTransaction.transaction.id.toString()
            } catch (e: Exception) {
                log.warn("Finality failed", e)
                "Finality failed, ${e.message}"
            }
        } catch (e: Exception) {
            log.warn("Failed to process utxo flow for request body '$requestBody' because:'${e.message}'")
            throw e
        }
    }
}

