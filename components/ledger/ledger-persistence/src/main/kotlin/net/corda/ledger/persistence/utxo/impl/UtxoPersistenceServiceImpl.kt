package net.corda.ledger.persistence.utxo.impl

import net.corda.data.membership.SignedGroupParameters
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.persistence.common.InconsistentLedgerStateException
import net.corda.ledger.persistence.utxo.CustomRepresentation
import net.corda.ledger.persistence.utxo.UtxoPersistenceService
import net.corda.ledger.persistence.utxo.UtxoRepository
import net.corda.ledger.persistence.utxo.UtxoTransactionReader
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.membership.lib.GroupParametersFactory
import net.corda.membership.lib.SignedGroupParameters as CordaSignedGroupParameters
import net.corda.orm.utils.transaction
import net.corda.utilities.serialization.deserialize
import net.corda.utilities.time.Clock
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateRef
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

@Suppress("LongParameterList")
class UtxoPersistenceServiceImpl constructor(
    private val entityManagerFactory: EntityManagerFactory,
    private val repository: UtxoRepository,
    private val serializationService: SerializationService,
    private val sandboxDigestService: DigestService,
    private val utcClock: Clock,
    private val groupParametersFactory: GroupParametersFactory
) : UtxoPersistenceService {

    override fun findTransaction(
        id: String,
        transactionStatus: TransactionStatus
    ): Pair<SignedTransactionContainer?, String?> {
        return entityManagerFactory.transaction { em ->
            val status = repository.findTransactionStatus(em, id)
            if (status == transactionStatus.value) {
                repository.findTransaction(em, id)
                    ?: throw InconsistentLedgerStateException("Transaction $id in status $status has disappeared from the database")
            } else {
                null
            } to status
        }
    }

    override fun <T: ContractState> findUnconsumedVisibleStatesByType(stateClass: Class<out T>): List<UtxoTransactionOutputDto> {
        val outputsInfoIdx = UtxoComponentGroup.OUTPUTS_INFO.ordinal
        val outputsIdx = UtxoComponentGroup.OUTPUTS.ordinal
        val componentGroups = entityManagerFactory.transaction { em ->
            repository.findUnconsumedVisibleStatesByType(em, listOf(outputsInfoIdx, outputsIdx))
        }.groupBy { it.groupIndex }
        val outputInfos = componentGroups[outputsInfoIdx]
            ?.associate { Pair(it.leafIndex, it.data) }
            ?: emptyMap()
        return componentGroups[outputsIdx]?.mapNotNull {
            val info = outputInfos[it.leafIndex]
            requireNotNull(info) {
                "Missing output info at index [${it.leafIndex}] for UTXO transaction with ID [${it.transactionId}]"
            }
            val contractState = serializationService.deserialize<ContractState>(it.data)
            if (stateClass.isInstance(contractState)) {
                UtxoTransactionOutputDto(it.transactionId, it.leafIndex, info, it.data)
            } else {
                null
            }
        } ?: emptyList()
    }

    override fun resolveStateRefs(stateRefs: List<StateRef>): List<UtxoTransactionOutputDto> {
        val outputsInfoIdx = UtxoComponentGroup.OUTPUTS_INFO.ordinal
        val outputsIdx = UtxoComponentGroup.OUTPUTS.ordinal
        val componentGroups = entityManagerFactory.transaction { em ->
            repository.resolveStateRefs(em, stateRefs, listOf(outputsInfoIdx, outputsIdx))
        }.groupBy { it.groupIndex }
        val outputInfos = componentGroups[outputsInfoIdx]
            ?.associate { Pair(it.leafIndex, it.data) }
            ?: emptyMap()
        return componentGroups[outputsIdx]?.map {
            val info = outputInfos[it.leafIndex]
            requireNotNull(info) {
                "Missing output info at index [${it.leafIndex}] for UTXO transaction with ID [${it.transactionId}]"
            }
            UtxoTransactionOutputDto(it.transactionId, it.leafIndex, info, it.data)
        } ?: emptyList()
    }

    override fun persistTransaction(transaction: UtxoTransactionReader): List<CordaPackageSummary> {
        entityManagerFactory.transaction { em ->
            return persistTransaction(em, transaction)
        }
    }

    private fun persistTransaction(em: EntityManager, transaction: UtxoTransactionReader): List<CordaPackageSummary> {
        val nowUtc = utcClock.instant()
        val transactionIdString = transaction.id.toString()

        // Insert the Transaction
        repository.persistTransaction(
            em,
            transactionIdString,
            transaction.privacySalt.bytes,
            transaction.account,
            nowUtc
        )

        // Insert the Transactions components
        transaction.rawGroupLists.mapIndexed { groupIndex, leaves ->
            leaves.mapIndexed { leafIndex, data ->
                repository.persistTransactionComponentLeaf(
                    em,
                    transactionIdString,
                    groupIndex,
                    leafIndex,
                    data,
                    sandboxDigestService.hash(data, DigestAlgorithmName.SHA2_256).toString(),
                    nowUtc
                )
            }
        }

        // Insert inputs data
        val inputs = transaction.getConsumedStateRefs()
        inputs.forEachIndexed { index, input ->
            repository.persistTransactionSource(
                em,
                transactionIdString,
                UtxoComponentGroup.INPUTS.ordinal,
                index,
                input.transactionId.toString(),
                input.index,
                false,
                nowUtc
            )
        }

        // Insert outputs data
        transaction.getProducedStates().forEachIndexed { index, stateAndRef ->
            repository.persistTransactionOutput(
                em,
                transactionIdString,
                UtxoComponentGroup.OUTPUTS.ordinal,
                index,
                stateAndRef.state.contractState::class.java.canonicalName,
                timestamp = nowUtc
            )
        }

        // Insert relevancy information for outputs
        transaction.visibleStatesIndexes.forEach { visibleStateIndex ->
            repository.persistTransactionVisibleStates(
                em,
                transactionIdString,
                UtxoComponentGroup.OUTPUTS.ordinal,
                visibleStateIndex,
                consumed = false,
                CustomRepresentation("{\"temp\": \"value\"}"),
                nowUtc
            )
        }

        // Mark inputs as consumed
        if (transaction.status == TransactionStatus.VERIFIED) {
            val inputStateRefs = transaction.getConsumedStateRefs()
            if (inputStateRefs.isNotEmpty()) {
                repository.markTransactionVisibleStatesConsumed(
                    em,
                    inputStateRefs,
                    nowUtc
                )
            }
        }

        // Insert the Transactions signatures
        transaction.signatures.forEachIndexed { index, digitalSignatureAndMetadata ->
            repository.persistTransactionSignature(
                em,
                transactionIdString,
                index,
                digitalSignatureAndMetadata,
                nowUtc
            )
        }

        // Insert the transactions current status
        repository.persistTransactionStatus(
            em,
            transactionIdString,
            transaction.status,
            nowUtc
        )

        // Insert the CPK details liked to this transaction
        // TODOs: The CPK file meta does not exist yet, this will be implemented by
        // https://r3-cev.atlassian.net/browse/CORE-7626
        return emptyList()
    }

    override fun persistTransactionIfDoesNotExist(transaction: UtxoTransactionReader): Pair<String?, List<CordaPackageSummary>> {
        entityManagerFactory.transaction { em ->
            val transactionIdString = transaction.id.toString()

            val status = repository.findTransactionStatus(em, transactionIdString)

            if (status != null) {
                return status to emptyList()
            }

            val cpkDetails = persistTransaction(em, transaction)

            return null to cpkDetails
        }
    }

    override fun updateStatus(id: String, transactionStatus: TransactionStatus) {
        entityManagerFactory.transaction { em ->
            repository.persistTransactionStatus(em, id, transactionStatus, utcClock.instant())
        }
    }

    override fun findSignedGroupParameters(hash: String): SignedGroupParameters? {
        return entityManagerFactory.transaction { em ->
            repository.findSignedGroupParameters(em, hash)
        }
    }

    override fun persistSignedGroupParametersIfDoNotExist(signedGroupParameters: SignedGroupParameters) {
        val cordaSignedGroupParameters = groupParametersFactory.create(signedGroupParameters) as CordaSignedGroupParameters
        val hash = cordaSignedGroupParameters.hash.toString()
        if (findSignedGroupParameters(hash) == null) {
            entityManagerFactory.transaction { em ->
                repository.persistSignedGroupParameters(
                    em,
                    hash = hash,
                    parameters = signedGroupParameters.groupParameters.array(),
                    signaturePublicKey = signedGroupParameters.mgmSignature.publicKey.array(),
                    signatureContent = signedGroupParameters.mgmSignature.bytes.array(),
                    signatureSpec = signedGroupParameters.mgmSignatureSpec.signatureName
                )
            }
        }
    }
}
