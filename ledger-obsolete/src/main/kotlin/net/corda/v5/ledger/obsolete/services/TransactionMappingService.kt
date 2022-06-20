package net.corda.v5.ledger.obsolete.services

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.ledger.obsolete.contracts.AttachmentResolutionException
import net.corda.v5.ledger.obsolete.contracts.TransactionResolutionException
import net.corda.v5.ledger.obsolete.transactions.LedgerTransaction
import net.corda.v5.ledger.obsolete.transactions.SignedTransaction
import net.corda.v5.ledger.obsolete.transactions.WireTransaction
import java.security.SignatureException

@DoNotImplement
interface TransactionMappingService {

    /**
     * @throws AttachmentResolutionException if a required attachment was not found in storage.
     * @throws TransactionResolutionException if an input points to a transaction not found in storage.
     */
    @Throws(SignatureException::class)
    fun toLedgerTransaction(transaction: SignedTransaction, checkSufficientSignatures: Boolean): LedgerTransaction

    /**
     * @throws AttachmentResolutionException if a required attachment was not found in storage.
     * @throws TransactionResolutionException if an input points to a transaction not found in storage.
     */
    @Throws(SignatureException::class)
    fun toLedgerTransaction(transaction: SignedTransaction): LedgerTransaction

    /**
     * Looks up identities and attachments from storage to generate a [LedgerTransaction]. A transaction is expected to
     * have been fully resolved using the resolution flow by this point.
     *
     * @throws AttachmentResolutionException if a required attachment was not found in storage.
     * @throws TransactionResolutionException if an input points to a transaction not found in storage.
     */
    @Throws(AttachmentResolutionException::class, TransactionResolutionException::class)
    fun toLedgerTransaction(transaction: WireTransaction): LedgerTransaction
}