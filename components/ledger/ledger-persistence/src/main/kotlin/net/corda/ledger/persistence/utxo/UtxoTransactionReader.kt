package net.corda.ledger.persistence.utxo

import net.corda.ledger.common.data.transaction.CordaPackageSummary
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.PrivacySalt
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef

interface UtxoTransactionReader {

    val id: SecureHash

    val account: String

    val status: String

    val privacySalt: PrivacySalt

    val rawGroupLists: List<List<ByteArray>>

    val cpkMetadata: List<CordaPackageSummary>

    fun getProducedStates(): List<StateAndRef<ContractState>>

    fun getConsumedStates(): List<StateAndRef<ContractState>>
}
