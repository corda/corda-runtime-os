package com.r3.corda.demo.interop.tokens.workflows.interop

import com.r3.corda.demo.interop.tokens.contracts.TokenContract
import com.r3.corda.demo.interop.tokens.states.TokenState
import com.r3.corda.demo.interop.tokens.workflows.IssueFlowResult
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.interop.FacadeService
import net.corda.v5.application.interop.InteropIdentityLookup
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.Duration
import java.time.Instant
import java.util.*

data class UnlockFlowArgs(val amount: String, val applicationName: String)

@InitiatingFlow(protocol = "dummy-unlock-123-protocol")
class UnlockFlowDispatcher: ClientStartableFlow {

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
    lateinit var facadeService: FacadeService

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var interopIdentityLookUp: InteropIdentityLookup

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        val facadeId = "org.corda.interop/platform/lock/v1.0"
        try {
            val flowArgs = requestBody.getRequestBodyAs(jsonMarshallingService, UnlockFlowArgs::class.java)

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

            //val notarySig: DigitalSignatureAndMetadata = finalizeTx(signedTransaction, listOf())

            val interopIdentity = checkNotNull(interopIdentityLookUp.lookup(flowArgs.applicationName)) {
                "No interop identity found with application name '${flowArgs.applicationName}'"
            }

            val lockFacade: LockFacade =
                facadeService.getProxy(facadeId, LockFacade::class.java, interopIdentity)

            val byteArrayKey: ByteArray = notary.publicKey.encoded
            val byteBuffer: ByteBuffer = ByteBuffer.wrap(byteArrayKey)
            lockFacade.createLock("stateId", "recipient",
                byteBuffer, signedTransaction.id.toString())
            return jsonMarshallingService.format(IssueFlowResult("124", outputState.linearId.toString()))

        } catch (e: Exception) {
            log.warn("Failed to process utxo flow for request body '$requestBody' because: '${e.message}'")
            throw e
        }
    }

    @Suspendable
    private fun finalizeTx(signedTransaction: UtxoSignedTransaction,otherMember: List<MemberX500Name>): DigitalSignatureAndMetadata {

        val sessions = otherMember.map { flowMessaging.initiateFlow(it) }

        val finalizedSignedTransaction = ledgerService.finalize(
            signedTransaction,
            sessions
        )

        finalizedSignedTransaction.transaction.signatures.forEach {
            it.signature.by
        }

        finalizedSignedTransaction.transaction.id.toString().also {
            log.info("Success! Response: $it")
        }

        return finalizedSignedTransaction.transaction.signatures.first { it.proof != null }
    }
}