package net.corda.ledger.persistence.utxo.impl

import com.fasterxml.jackson.core.JsonProcessingException
import net.corda.common.json.validation.JsonValidator
import net.corda.crypto.cipher.suite.merkle.MerkleProofFactory
import net.corda.crypto.cipher.suite.merkle.MerkleProofInternal
import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.crypto.core.parseSecureHash
import net.corda.data.membership.SignedGroupParameters
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionMetadataInternal
import net.corda.ledger.common.data.transaction.TransactionMetadataUtils.createHashDigestProvider
import net.corda.ledger.common.data.transaction.TransactionMetadataUtils.createTopLevelDigestProvider
import net.corda.ledger.common.data.transaction.TransactionMetadataUtils.parseMetadata
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.data.transaction.filtered.FilteredComponentGroup
import net.corda.ledger.common.data.transaction.filtered.FilteredTransaction
import net.corda.ledger.common.data.transaction.filtered.factory.FilteredTransactionFactory
import net.corda.ledger.persistence.common.InconsistentLedgerStateException
import net.corda.ledger.persistence.json.ContractStateVaultJsonFactoryRegistry
import net.corda.ledger.persistence.json.DefaultContractStateVaultJsonFactory
import net.corda.ledger.persistence.utxo.CustomRepresentation
import net.corda.ledger.persistence.utxo.UtxoPersistenceService
import net.corda.ledger.persistence.utxo.UtxoRepository
import net.corda.ledger.persistence.utxo.UtxoTransactionReader
import net.corda.ledger.utxo.data.transaction.SignedLedgerTransactionContainer
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.ledger.utxo.data.transaction.UtxoVisibleTransactionOutputDto
import net.corda.ledger.utxo.data.transaction.WrappedUtxoWireTransaction
import net.corda.libs.packaging.hash
import net.corda.orm.utils.transaction
import net.corda.utilities.serialization.deserialize
import net.corda.utilities.time.Clock
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.merkle.MerkleProof
import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.observer.UtxoToken
import net.corda.v5.ledger.utxo.query.json.ContractStateVaultJsonFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

@Suppress("LongParameterList", "TooManyFunctions")
class UtxoPersistenceServiceImpl(
    private val entityManagerFactory: EntityManagerFactory,
    private val repository: UtxoRepository,
    private val serializationService: SerializationService,
    private val sandboxDigestService: DigestService,
    private val factoryStorage: ContractStateVaultJsonFactoryRegistry,
    private val defaultContractStateVaultJsonFactory: DefaultContractStateVaultJsonFactory,
    private val jsonMarshallingService: JsonMarshallingService,
    private val jsonValidator: JsonValidator,
    private val merkleProofFactory: MerkleProofFactory,
    private val merkleTreeProvider: MerkleTreeProvider,
    private val filteredTransactionFactory: FilteredTransactionFactory,
    private val utcClock: Clock
) : UtxoPersistenceService {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(UtxoPersistenceServiceImpl::class.java)
    }

    override fun findSignedTransaction(
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

    override fun findTransactionIdsAndStatuses(
        transactionIds: List<String>
    ): Map<SecureHash, String> {
        return entityManagerFactory.transaction { em ->
            repository.findTransactionIdsAndStatuses(em, transactionIds)
        }
    }

    override fun findSignedLedgerTransaction(
        id: String,
        transactionStatus: TransactionStatus
    ): Pair<SignedLedgerTransactionContainer?, String?> {
        return entityManagerFactory.transaction { em ->
            val status = repository.findTransactionStatus(em, id)
            if (status == transactionStatus.value) {
                val (transaction, signatures) = repository.findTransaction(em, id)
                    ?.let { WrappedUtxoWireTransaction(it.wireTransaction, serializationService) to it.signatures }
                    ?: throw InconsistentLedgerStateException("Transaction $id in status $status has disappeared from the database")

                val allStateRefs = (transaction.inputStateRefs + transaction.referenceStateRefs).distinct()

                // Note: calling the `resolveStateRefs` function would result in a new connection being established,
                // so we call the repository directly instead
                val stateRefsToStateAndRefs = repository.resolveStateRefs(em, allStateRefs)
                    .associateBy { StateRef(parseSecureHash(it.transactionId), it.leafIndex) }

                val inputStateAndRefs = transaction.inputStateRefs.map {
                    stateRefsToStateAndRefs[it]
                        ?: throw CordaRuntimeException("Could not find input StateRef $it when finding transaction $id")
                }
                val referenceStateAndRefs = transaction.referenceStateRefs.map {
                    stateRefsToStateAndRefs[it]
                        ?: throw CordaRuntimeException("Could not find reference StateRef $it when finding transaction $id")
                }

                SignedLedgerTransactionContainer(transaction.wireTransaction, inputStateAndRefs, referenceStateAndRefs, signatures)
            } else {
                null
            } to status
        }
    }

    override fun <T : ContractState> findUnconsumedVisibleStatesByType(stateClass: Class<out T>): List<UtxoVisibleTransactionOutputDto> {
        return entityManagerFactory.transaction { em ->
            repository.findUnconsumedVisibleStatesByType(em)
        }.filter {
            val contractState = serializationService.deserialize<ContractState>(it.data)
            stateClass.isInstance(contractState)
        }
    }

    override fun resolveStateRefs(stateRefs: List<StateRef>): List<UtxoVisibleTransactionOutputDto> {
        return entityManagerFactory.transaction { em ->
            repository.resolveStateRefs(em, stateRefs)
        }
    }

    override fun persistTransaction(transaction: UtxoTransactionReader, utxoTokenMap: Map<StateRef, UtxoToken>): List<CordaPackageSummary> {
        entityManagerFactory.transaction { em ->
            return persistTransaction(em, transaction, utxoTokenMap)
        }
    }

    private fun persistTransaction(
        em: EntityManager,
        transaction: UtxoTransactionReader,
        utxoTokenMap: Map<StateRef, UtxoToken> = emptyMap()
    ): List<CordaPackageSummary> {
        val nowUtc = utcClock.instant()
        val transactionIdString = transaction.id.toString()

        val metadataBytes = transaction.rawGroupLists[0][0]
        val metadataHash = sandboxDigestService.hash(metadataBytes, DigestAlgorithmName.SHA2_256).toString()

        val metadata = transaction.metadata
        repository.persistTransactionMetadata(
            em,
            metadataHash,
            metadataBytes,
            requireNotNull(metadata.getMembershipGroupParametersHash()) { "Metadata without membership group parameters hash" },
            requireNotNull(metadata.getCpiMetadata()) { "Metadata without CPI metadata" }.fileChecksum
        )

        // Insert the Transaction
        repository.persistTransaction(
            em,
            transactionIdString,
            transaction.privacySalt.bytes,
            transaction.account,
            nowUtc,
            transaction.status,
            metadataHash,
            false
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
                    sandboxDigestService.hash(data, DigestAlgorithmName.SHA2_256).toString()
                )
            }
        }

        // Insert inputs data
        transaction.getConsumedStateRefs().forEachIndexed { index, input ->
            repository.persistTransactionSource(
                em,
                transactionIdString,
                UtxoComponentGroup.INPUTS.ordinal,
                index,
                input.transactionId.toString(),
                input.index
            )
        }

        // Insert reference data
        transaction.getReferenceStateRefs().forEachIndexed { index, reference ->
            repository.persistTransactionSource(
                em,
                transactionIdString,
                UtxoComponentGroup.REFERENCES.ordinal,
                index,
                reference.transactionId.toString(),
                reference.index
            )
        }

        // Insert outputs data
        transaction.getVisibleStates().entries.forEach { (stateIndex, stateAndRef) ->
            val utxoToken = utxoTokenMap[stateAndRef.ref]
            repository.persistVisibleTransactionOutput(
                em,
                transactionIdString,
                UtxoComponentGroup.OUTPUTS.ordinal,
                stateIndex,
                stateAndRef.state.contractState::class.java.canonicalName,
                nowUtc,
                consumed = false,
                CustomRepresentation(extractJsonDataFromState(stateAndRef)),
                utxoToken?.poolKey?.tokenType,
                utxoToken?.poolKey?.issuerHash?.toString(),
                stateAndRef.state.notaryName.toString(),
                utxoToken?.poolKey?.symbol,
                utxoToken?.filterFields?.tag,
                utxoToken?.filterFields?.ownerHash?.toString(),
                utxoToken?.amount
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
            repository.updateTransactionStatus(em, id, transactionStatus, utcClock.instant())
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
                log.warn("Error while processing factory for class: ${it.stateType.name}. Defaulting to empty JSON.", e)
                "{}"
            }

            it.stateType.name to try {
                jsonMarshallingService.parse(jsonToParse, Any::class.java)
            } catch (e: Exception) {
                log.warn(
                    "Error while processing factory for class: ${it.stateType.name}. " +
                        "JSON that could not be processed: $jsonToParse. Defaulting to empty JSON.",
                    e
                )
                jsonMarshallingService.parse("{}", Any::class.java)
            }
        }.toMutableMap()

        try {
            jsonMap[ContractState::class.java.name] = jsonMarshallingService.parse(
                defaultContractStateVaultJsonFactory.create(stateAndRef, jsonMarshallingService),
                Any::class.java
            )
        } catch (e: Exception) {
            log.warn("Error while processing factory for class: ${ContractState::class.java.name}. Defaulting to empty JSON.", e)
            jsonMarshallingService.parse("{}", Any::class.java)
        }

        return try {
            jsonMarshallingService.format(jsonMap)
        } catch (e: JsonProcessingException) {
            // Since we validate the factory outputs one-by-one this should not happen.
            log.warn("Error while formatting combined JSON, defaulting to empty JSON.", e)
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

    override fun persistFilteredTransactions(
        filteredTransactions: List<FilteredTransaction>,
        signatures: List<DigitalSignatureAndMetadata>,
        account: String
    ) {
        entityManagerFactory.transaction { em ->

            filteredTransactions.forEach { filteredTransaction ->

                val nowUtc = utcClock.instant()

                val metadata = filteredTransaction.metadata as TransactionMetadataInternal

                // 1. Map metadata to a JSON format
                val metadataBytes = jsonValidator.canonicalize(
                    jsonMarshallingService.format(metadata)
                ).toByteArray()

                val metadataHash = sandboxDigestService.hash(
                    metadataBytes,
                    DigestAlgorithmName.SHA2_256
                ).toString()

                // 2. Persist the transaction metadata
                repository.persistTransactionMetadata(
                    em,
                    metadataHash,
                    metadataBytes,
                    requireNotNull(metadata.getMembershipGroupParametersHash()) {
                        "Metadata without membership group parameters hash"
                    },
                    requireNotNull(metadata.getCpiMetadata()) {
                        "Metadata without CPI metadata"
                    }.fileChecksum
                )

                // 3. Persist the transaction itself to the utxo_transaction table
                repository.persistTransaction(
                    em,
                    filteredTransaction.id.toString(),
                    filteredTransaction.privacySalt.bytes,
                    account,
                    nowUtc,
                    TransactionStatus.VERIFIED,
                    metadataHash,
                    isFiltered = true
                )

                // 4. Persist the signatures
                signatures.forEachIndexed { index, digitalSignatureAndMetadata ->
                    repository.persistTransactionSignature(
                        em,
                        filteredTransaction.id.toString(),
                        index,
                        digitalSignatureAndMetadata,
                        nowUtc
                    )
                }

                // 5. Persist the top level merkle proof and its leaves data
                persistMerkleProofAndLeavesData(
                    em,
                    filteredTransaction.id,
                    -1,
                    filteredTransaction.topLevelMerkleProof
                )

                // 6. Persist the merkle proof and leaf data for each component group
                filteredTransaction.filteredComponentGroups.forEach { (groupIndex, groupData) ->
                    persistMerkleProofAndLeavesData(
                        em,
                        filteredTransaction.id,
                        groupIndex,
                        groupData.merkleProof
                    )
                }
            }
        }
    }

    private fun findFilteredTransactions(
        ids: List<String>
    ): Map<String, Pair<FilteredTransaction, List<DigitalSignatureAndMetadata>>> {
        return entityManagerFactory.transaction { em ->
            repository.findFilteredTransactions(em, ids)
        }.map { (transactionId, ftxDto) ->
            // Map through each found transaction

            // 1. Parse the metadata bytes
            val filteredTransactionMetadata = parseMetadata(
                ftxDto.metadataBytes,
                jsonValidator,
                jsonMarshallingService
            )

            // 2. Merge the Merkle proofs for each component group
            val hashDigestProvider = createHashDigestProvider(filteredTransactionMetadata, merkleTreeProvider)
            val mergedMerkleProofs = ftxDto.componentMerkleProofMap.mapValues { (_, merkleProofDtoList) ->

                merkleProofDtoList.map { merkleProofDto ->
                    // Transform the MerkleProofDto objects to MerkleProof objects
                    merkleProofFactory.createAuditMerkleProof(
                        merkleProofDto.transactionId,
                        merkleProofDto.treeSize,
                        merkleProofDto.leavesWithData,
                        merkleProofDto.hashes,
                        hashDigestProvider
                    )
                }.reduce { accumulator, merkleProof ->
                    // Then  keep merging the elements into each other
                    (accumulator as MerkleProofInternal).merge(
                        merkleProof,
                        hashDigestProvider
                    )
                }
            }

            // 3. Create the top level Merkle proof
            val topLevelMerkleProof = merkleProofFactory.createAuditMerkleProof(
                transactionId,
                ftxDto.topLevelMerkleProof.treeSize,
                ftxDto.topLevelMerkleProof.leavesWithData,
                ftxDto.topLevelMerkleProof.hashes,
                createTopLevelDigestProvider(filteredTransactionMetadata, merkleTreeProvider)
            )

            // 4. Create the filtered transaction object
            val filteredTransaction = filteredTransactionFactory.create(
                parseSecureHash(transactionId),
                topLevelMerkleProof,
                mergedMerkleProofs.map {
                    it.key to FilteredComponentGroup(it.key, it.value)
                }.toMap(),
                ftxDto.privacySalt.bytes
            )

            // 5. Map the transaction id to the filtered transaction object and signatures
            filteredTransaction.id.toString() to Pair(filteredTransaction, ftxDto.signatures)
        }.toMap()
    }

    private fun persistMerkleProofAndLeavesData(
        em: EntityManager,
        transactionId: SecureHash,
        componentGroupIndex: Int,
        merkleProof: MerkleProof
    ) {
        val merkleProofId = repository.persistMerkleProof(
            em,
            transactionId.toString(),
            componentGroupIndex,
            merkleProof.treeSize,
            merkleProof.leaves.map { it.index },
            merkleProof.hashes.map { it.toString() }
        )

        merkleProof.leaves.forEach { leaf ->
            repository.persistMerkleProofLeaf(
                em,
                merkleProofId,
                leaf.index
            )

            repository.persistTransactionComponentLeaf(
                em,
                transactionId.toString(),
                componentGroupIndex,
                leaf.index,
                leaf.leafData,
                sandboxDigestService.hash(leaf.leafData, DigestAlgorithmName.SHA2_256).toString()
            )
        }
    }
}
