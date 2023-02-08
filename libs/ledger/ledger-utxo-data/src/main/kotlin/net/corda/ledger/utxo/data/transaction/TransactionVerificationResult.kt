package net.corda.ledger.utxo.data.transaction

data class TransactionVerificationResult(
    val status: TransactionVerificationStatus,
    val errorType: String? = null,
    val errorMessage: String? = null
)