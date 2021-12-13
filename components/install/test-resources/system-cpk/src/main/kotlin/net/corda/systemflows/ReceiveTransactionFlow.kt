package net.corda.systemflows

import java.security.SignatureException
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.FlowSession
import net.corda.v5.application.flows.receive
import net.corda.v5.application.flows.unwrap
import net.corda.v5.application.injection.CordaInject
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.trace
import net.corda.v5.ledger.services.StatesToRecord
import net.corda.v5.ledger.services.TransactionService
import net.corda.v5.ledger.services.TransactionVerificationService
import net.corda.v5.ledger.transactions.SignedTransaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory

open class ReceiveTransactionFlow constructor(
    private val otherSideSession: FlowSession,
    private val checkSufficientSignatures: Boolean,
    private val statesToRecord: StatesToRecord
) : Flow<SignedTransaction> {

    constructor(otherSideSession: FlowSession, checkSufficientSignatures: Boolean) : this(
        otherSideSession,
        checkSufficientSignatures,
        StatesToRecord.NONE
    )

    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @CordaInject
    lateinit var transactionService: TransactionService
    @CordaInject
    lateinit var transactionVerificationService: TransactionVerificationService

    @Suppress("KDocMissingDocumentation")
    @Suspendable
    @Throws(SignatureException::class)
    override fun call(): SignedTransaction {
        if (checkSufficientSignatures) {
            logger.trace { "Receiving a transaction from ${otherSideSession.counterparty}" }
        } else {
            logger.trace { "Receiving a transaction (but without checking the signatures) from ${otherSideSession.counterparty}" }
        }
        val stx = otherSideSession.receive<SignedTransaction>().unwrap {
            logger.info("Transaction dependencies resolution completed.")
            try {
                transactionVerificationService.verify(it, checkSufficientSignatures)
                it
            } catch (e: Exception) {
                logger.warn("Transaction verification failed.")
                throw e
            }
        }
        if (checkSufficientSignatures) {
            // We should only send a transaction to the vault for processing if we did in fact fully verify it, and
            // there are no missing signatures. We don't want partly signed stuff in the vault.
            checkBeforeRecording(stx)
            logger.info("Successfully received fully signed tx. Sending it to the vault for processing.")
            transactionService.record(statesToRecord, setOf(stx))
            logger.info("Successfully recorded received transaction locally.")
        }
        return stx
    }

    /**
     * Hook to perform extra checks on the received transaction just before it's recorded. The transaction has already
     * been resolved and verified at this point.
     */
    @Suspendable
    protected open fun checkBeforeRecording(stx: SignedTransaction) = Unit
}
