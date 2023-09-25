package com.r3.corda.demo.interop.tokens.workflows.interop

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.ledger.common.transaction.TransactionSignatureVerificationService
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.*

class LockFlow: FacadeDispatcherFlow(), LockFacade{

    @CordaInject
    lateinit var transactionSignatureVerificationService: TransactionSignatureVerificationService

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }
    override fun createLock(denomination: String, amount: BigDecimal, notaryKeys: String, draftTxId: String): UUID {
        TODO("Not yet implemented")
    }

    override fun unlock(reservationRef: UUID, proof: DigitalSignatureAndMetadata, key: ByteArray): BigDecimal {
        log.info("Here is the secure hash" + proof.by.toString())
        val x509publicKey = X509EncodedKeySpec(key)
        val kf: KeyFactory = KeyFactory.getInstance("EC")
        log.info(proof.toString())

        transactionSignatureVerificationService.verifySignature(proof.by,proof,kf.generatePublic(x509publicKey))
        return BigDecimal(1)
    }
}