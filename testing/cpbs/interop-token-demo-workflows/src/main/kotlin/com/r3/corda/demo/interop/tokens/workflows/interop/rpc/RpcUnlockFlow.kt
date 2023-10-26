package com.r3.corda.demo.interop.tokens.workflows.interop.rpc

import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.marshalling.InteropJsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.common.transaction.TransactionSignatureVerificationService
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.*

data class RpcUnlockFlowResult(val transactionId: String, val stateId: UUID, val signature: DigitalSignatureAndMetadata,
    val publickKey: ByteBuffer)


class RpcUnlockFlow: ClientStartableFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var jsonMarshallingService: InteropJsonMarshallingService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @CordaInject
    lateinit var transactionSignatureVerificationService: TransactionSignatureVerificationService

    @CordaInject
    lateinit var digestService: DigestService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("RpcUnlockFlow.call() called")

        try {
            val flowArgs = requestBody.getRequestBodyAs(jsonMarshallingService, RpcTransferFlowResult::class.java)

            val proof = flowArgs.signature
            val key = flowArgs.publickKey

            val secureHash = digestService.parseSecureHash(flowArgs.transactionId)

            log.info("Here is the secure hash" + proof.by.toString())
            val x509publicKey = X509EncodedKeySpec(key.array())
            val kf: KeyFactory = KeyFactory.getInstance("EC")
            val publicKey = kf.generatePublic(x509publicKey)

            try {
                transactionSignatureVerificationService.verifySignature(secureHash, proof, publicKey)
            } catch (e: Exception) {
                log.error("Transaction id $secureHash doesn't match the proof $proof signed by" +
                        " ${Base64.getEncoder().encodeToString(publicKey.encoded)}, reason: ${e.message}")
                throw e
            }
            log.info("Transaction id $secureHash is matching the proof $proof signed by " +
                    Base64.getEncoder().encodeToString(publicKey.encoded)
            )

            return jsonMarshallingService.format(RpcUnlockFlowResult(flowArgs.transactionId, flowArgs.stateId,
                flowArgs.signature, flowArgs.publickKey))

        } catch (e: Exception) {
            log.warn("Failed to process utxo flow for request body '$requestBody' because: '${e.message}'")
            throw e
        }
    }
}
