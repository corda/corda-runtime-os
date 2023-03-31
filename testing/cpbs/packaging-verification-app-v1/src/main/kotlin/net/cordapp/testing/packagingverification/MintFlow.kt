package net.cordapp.testing.packagingverification

import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.membership.NotaryInfo
import net.cordapp.testing.packagingverification.contract.MintCommand
import net.cordapp.testing.packagingverification.contract.SimpleState
import org.slf4j.LoggerFactory
import java.security.PublicKey
import java.time.Duration
import java.time.Instant

class MintFlow : ClientStartableFlow {

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
    lateinit var utxoLedgerService: UtxoLedgerService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("Minting states")

        val myInfo = memberLookup.myInfo()

        val mintRequest = requestBody.getRequestBodyAs(jsonMarshallingService, MintRequest::class.java)
        require(mintRequest.stateValues.isNotEmpty()) { "No state values to mint" }

        val myPublicKey = myInfo.ledgerKeys.first()
        val notary = notaryLookup.notaryServices.first()

        log.info("Minting ${mintRequest.stateValues.size} states")
        mintStates(mintRequest.stateValues, myPublicKey, notary, myInfo.name)

        log.info("Finished minting ${mintRequest.stateValues.size} states")
        return ""
    }

    @Suspendable
    fun mintStates(values: List<Long>, publicKey: PublicKey, notary: NotaryInfo, issuer: MemberX500Name) {
        val states = values.map { SimpleState(it, listOf(publicKey), issuer) }

        log.info("Creating signed transaction")

        val signedTransaction = utxoLedgerService.createTransactionBuilder()
            .setNotary(notary.name)
            .addOutputStates(states)
            .addSignatories(listOf(publicKey))
            .setTimeWindowUntil(Instant.now() + Duration.ofDays(1))
            .addCommand(MintCommand())
            .toSignedTransaction()

        log.info("Finalizing signed transaction")

        utxoLedgerService.finalize(
            signedTransaction,
            emptyList() // minting coins, no counterparty
        )

        log.info("Done finalizing")
    }
}
