package net.cordapp.testing.packagingverification

import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.membership.NotaryInfo
import net.cordapp.testing.packagingverification.contract.SimpleCommand
import net.cordapp.testing.packagingverification.contract.SimpleState
import org.slf4j.LoggerFactory
import java.security.MessageDigest
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

    val issuer = MemberX500Name.parse("C=GB, L=London, O=Bob")

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        val myInfo = memberLookup.myInfo()

        if (myInfo.name != issuer) {
            throw CordaRuntimeException("Mint flow must be executed by ${issuer.commonName}")
        }
        val mintRequest = requestBody.getRequestBodyAs(jsonMarshallingService, MintRequest::class.java)
        if (mintRequest.stateValues.isEmpty()) {
            throw CordaRuntimeException("No state values to mint")
        }

        val myPublicKey = myInfo.ledgerKeys.first()
        val notary = notaryLookup.notaryServices.single()

        mintRequest.stateValues.forEach { stateValue ->
            log.info("Minting state with value $stateValue")
            mintState(stateValue, myPublicKey, notary)
        }

        return ""
    }

    @Suspendable
    fun mintState(value: Long, publicKey: PublicKey, notary: NotaryInfo) {
        val state = SimpleState(value, issuer.toSecureHash(), listOf(publicKey))

        val signedTransaction = utxoLedgerService.transactionBuilder
            .setNotary(Party(notary.name, notary.publicKey))
            .addOutputState(state)
            .addSignatories(state.participants)
            .setTimeWindowUntil(Instant.now() + Duration.ofDays(1))
            .addCommand(SimpleCommand())
            .toSignedTransaction()

        utxoLedgerService.finalize(
            signedTransaction,
            emptyList() // minting coins, no counterparty
        )
    }

    private fun MemberX500Name.toSecureHash(): SecureHash {
        val algorithm = DigestAlgorithmName.SHA2_256.name
        return SecureHash(algorithm, MessageDigest.getInstance(algorithm).digest(toString().toByteArray()))
    }
}
