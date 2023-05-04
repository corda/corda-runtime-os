package net.corda.ledger.persistence.utxo.impl

import com.fasterxml.jackson.core.JsonProcessingException
import net.corda.data.membership.SignedGroupParameters
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.persistence.common.InconsistentLedgerStateException
import net.corda.ledger.persistence.json.ContractStateVaultJsonFactoryRegistry
import net.corda.ledger.persistence.json.DefaultContractStateVaultJsonFactory
import net.corda.ledger.persistence.utxo.CustomRepresentation
import net.corda.ledger.persistence.utxo.UtxoPersistenceService
import net.corda.ledger.persistence.utxo.UtxoRepository
import net.corda.ledger.persistence.utxo.UtxoTransactionReader
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.ledger.utxo.data.transaction.UtxoTransactionOutputDto
import net.corda.libs.packaging.hash
import net.corda.orm.utils.transaction
import net.corda.utilities.serialization.deserialize
import net.corda.utilities.time.Clock
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.query.json.ContractStateVaultJsonFactory
import org.slf4j.LoggerFactory
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

@Suppress("LongParameterList")
class UtxoPersistenceServiceImpl(
    private val entityManagerFactory: EntityManagerFactory,
    private val repository: UtxoRepository,
    private val serializationService: SerializationService,
    private val sandboxDigestService: DigestService,
    private val factoryStorage: ContractStateVaultJsonFactoryRegistry,
    private val defaultContractStateVaultJsonFactory: DefaultContractStateVaultJsonFactory,
    private val jsonMarshallingService: JsonMarshallingService,
    private val utcClock: Clock
) : UtxoPersistenceService {

    private companion object {
        val log = LoggerFactory.getLogger(UtxoPersistenceServiceImpl::class.java)
    }

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
            ?.associateBy { it.transactionId to it.leafIndex }
            ?: emptyMap()

        return componentGroups[outputsIdx]?.map {
            val info = outputInfos[it.transactionId to it.leafIndex]
            requireNotNull(info) {
                "Missing output info at index [${it.leafIndex}] for UTXO transaction with ID [${it.transactionId}]"
            }
            UtxoTransactionOutputDto(it.transactionId, it.leafIndex, info.data, it.data)
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
        transaction.getVisibleStates().entries.forEach { (stateIndex, stateAndRef) ->
            repository.persistTransactionOutput(
                em,
                transactionIdString,
                UtxoComponentGroup.OUTPUTS.ordinal,
                stateIndex,
                stateAndRef.state.contractState::class.java.canonicalName,
                timestamp = nowUtc
            )
        }

        // Insert relevancy information for outputs
        transaction.visibleStatesIndexes.forEach { visibleStateIndex ->

            val jsonString = transaction.getVisibleStates()[visibleStateIndex]?.let {
                extractJsonDataFromState(it)
            } ?: run {
                log.warn("Could not find visible state with index $visibleStateIndex, defaulting to empty JSON string.")
                "{}"
            }

            repository.persistTransactionVisibleStates(
                em,
                transactionIdString,
                UtxoComponentGroup.OUTPUTS.ordinal,
                visibleStateIndex,
                consumed = false,
                CustomRepresentation(jsonString),
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

    private fun extractJsonDataFromState(stateAndRef: StateAndRef<*>): String {
        val contractState = stateAndRef.state.contractState
        val jsonMap = factoryStorage.getFactoriesForClass(contractState).associate {

            val jsonToParse = try {
                @Suppress("unchecked_cast")
                (it as ContractStateVaultJsonFactory<ContractState>)
                    .create(contractState, jsonMarshallingService)
                    .ifBlank { "{}" } // Default to "{}" if the provided factory returns empty string to avoid exception
            } catch (e: Exception) {
                // We can't log the JSON string here because the failed before we have a JSON
                log.warn("Error while processing factory for class: ${it.stateType.name}. Defaulting to empty JSON.")
                "{}"
            }

            it.stateType.name to try {
                jsonMarshallingService.parse(jsonToParse, Any::class.java)
            } catch (e: Exception) {
                log.warn("Error while processing factory for class: ${it.stateType.name}. " +
                        "JSON that could not be processed: $jsonToParse. Defaulting to empty JSON.")
                jsonMarshallingService.parse("{}", Any::class.java)
            }
        }.toMutableMap()

        try {
            jsonMap[ContractState::class.java.name] = jsonMarshallingService.parse(
                defaultContractStateVaultJsonFactory.create(stateAndRef, jsonMarshallingService),
                Any::class.java
            )
        } catch (e: Exception) {
            log.warn("Error while processing factory for class: ${ContractState::class.java.name}. Defaulting to empty JSON.")
            jsonMarshallingService.parse("{}", Any::class.java)
        }

        return try {
            jsonMarshallingService.format(jsonMap)
        } catch (e: JsonProcessingException) {
            // Since we validate the factory outputs one-by-one this should not happen.
            log.warn("Error while formatting combined JSON, defaulting to empty JSON.")
            "{}"
        }
    }

    override fun findSignedGroupParameters(hash: String): SignedGroupParameters? {
        return entityManagerFactory.transaction { em ->
            repository.findSignedGroupParameters(em, hash)
        }
    }

    override fun persistSignedGroupParametersIfDoNotExist(signedGroupParameters: SignedGroupParameters) {
        val hash = signedGroupParameters.groupParameters.array().hash(DigestAlgorithmName.SHA2_256).toString()
        if (findSignedGroupParameters(hash) == null) {
            entityManagerFactory.transaction { em ->
                repository.persistSignedGroupParameters(
                    em,
                    hash,
                    signedGroupParameters,
                    utcClock.instant()
                )
            }
        }
    }
}
