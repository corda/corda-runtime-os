package net.corda.ledger.verification.processor

interface VerificationService {
    fun verifyContracts(transaction: UtxoTransactionReader): Boolean
}
