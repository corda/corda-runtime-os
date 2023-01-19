package net.corda.ledger.utxo.data.transaction

import net.corda.v5.ledger.utxo.ContractVerificationFailure

data class ContractVerificationResult(
    val status: ContractVerificationStatus,
    val failureReasons: List<ContractVerificationFailure>
)