package net.corda.ledger.persistence.utxo

import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import net.corda.v5.ledger.common.transaction.PrivacySalt
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef

interface UtxoTransactionReader {

    val id: SecureHash

    val account: String

    val status: TransactionStatus

    val privacySalt: PrivacySalt

    val rawGroupLists: List<List<ByteArray>>

    val signatures: List<DigitalSignatureAndMetadata>

    val cpkMetadata: List<CordaPackageSummary>

    val relevantStatesIndexes: List<Int>

    fun getProducedStates(): List<StateAndRef<ContractState>>

    fun getConsumedStates(): List<StateAndRef<ContractState>>
}
