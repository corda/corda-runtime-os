package com.r3.corda.demo.interop.tokens.workflows.interop

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.common.transaction.TransactionSignatureVerificationService
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.*

@InitiatingFlow(protocol = "lock-responder-sub-flow")
class LockFlow: FacadeDispatcherFlow(), LockFacade{

    @CordaInject
    lateinit var transactionSignatureVerificationService: TransactionSignatureVerificationService

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }
    @Suspendable
    override fun createLock(denomination: String, amount: BigDecimal, notaryKeys: String, draft: String): UUID {
        TODO("Not yet implemented")
    }

    @Suspendable
    override fun unlock(reservationRef: UUID, proof: DigitalSignatureAndMetadata, key: ByteBuffer): BigDecimal {
        log.info("Here is the secure hash" + proof.by.toString())
        val x509publicKey = X509EncodedKeySpec(key.array())
        val kf: KeyFactory = KeyFactory.getInstance("EC")
        val publicKey = kf.generatePublic(x509publicKey)
        log.info(proof.toString())

        transactionSignatureVerificationService.verifySignature(proof.by, proof, publicKey)
        return BigDecimal.ONE
    }
}