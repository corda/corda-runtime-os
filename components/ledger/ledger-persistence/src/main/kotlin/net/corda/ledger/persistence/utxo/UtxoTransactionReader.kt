package net.corda.ledger.persistence.utxo

import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import net.corda.ledger.common.data.transaction.PrivacySalt
import net.corda.ledger.common.data.transaction.TransactionMetadataInternal
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef

interface UtxoTransactionReader {

    val id: SecureHash

    val metadata: TransactionMetadataInternal

    val account: String

    val status: TransactionStatus

    val privacySalt: PrivacySalt

    val rawGroupLists: List<List<ByteArray>>

    val signatures: List<DigitalSignatureAndMetadata>

    val cpkMetadata: List<CordaPackageSummary>

    val visibleStatesIndexes: List<Int>

    fun getVisibleStates(): Map<Int, StateAndRef<ContractState>>

    fun getConsumedStates(persistenceService: UtxoPersistenceService): List<StateAndRef<ContractState>>

    fun getConsumedStateRefs(): List<StateRef>
}
