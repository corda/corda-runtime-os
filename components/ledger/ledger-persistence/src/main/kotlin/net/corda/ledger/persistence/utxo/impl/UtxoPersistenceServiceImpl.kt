package net.corda.ledger.persistence.utxo.impl

import com.fasterxml.jackson.core.JsonProcessingException
import net.corda.common.json.validation.JsonValidator
import net.corda.crypto.cipher.suite.merkle.MerkleProofFactory
import net.corda.crypto.cipher.suite.merkle.MerkleProofInternal
import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.crypto.core.bytes
import net.corda.crypto.core.parseSecureHash
import net.corda.data.membership.SignedGroupParameters
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionMetadataInternal
import net.corda.ledger.common.data.transaction.TransactionMetadataUtils.parseMetadata
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.data.transaction.filtered.ComponentGroupFilterParameters
import net.corda.ledger.common.data.transaction.filtered.FilteredComponentGroup
import net.corda.ledger.common.data.transaction.filtered.FilteredTransaction
import net.corda.ledger.common.data.transaction.filtered.factory.FilteredTransactionFactory
import net.corda.ledger.common.data.transaction.getComponentGroupMerkleTreeDigestProvider
import net.corda.ledger.common.data.transaction.getRootMerkleTreeDigestProvider
import net.corda.ledger.persistence.common.InconsistentLedgerStateException
import net.corda.ledger.persistence.json.ContractStateVaultJsonFactoryRegistry
import net.corda.ledger.persistence.json.DefaultContractStateVaultJsonFactory
import net.corda.ledger.persistence.utxo.CustomRepresentation
import net.corda.ledger.persistence.utxo.UtxoPersistenceService
import net.corda.ledger.persistence.utxo.UtxoRepository
import net.corda.ledger.persistence.utxo.UtxoTransactionReader
import net.corda.ledger.utxo.data.transaction.SignedLedgerTransactionContainer
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup.METADATA
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup.NOTARY
import net.corda.ledger.utxo.data.transaction.UtxoOutputInfoComponent
import net.corda.ledger.utxo.data.transaction.UtxoVisibleTransactionOutputDto
import net.corda.ledger.utxo.data.transaction.WrappedUtxoWireTransaction
import net.corda.ledger.utxo.data.transaction.toMerkleProof
import net.corda.libs.packaging.hash
import net.corda.metrics.CordaMetrics
import net.corda.orm.utils.transaction
import net.corda.utilities.serialization.deserialize
import net.corda.utilities.time.Clock
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProvider
import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.observer.UtxoToken
import net.corda.v5.ledger.utxo.query.json.ContractStateVaultJsonFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
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
    private val digestService: DigestService,
    private val utcClock: Clock
) : UtxoPersistenceService {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(UtxoPersistenceServiceImpl::class.java)

        const val TOP_LEVEL_MERKLE_PROOF_GROUP_INDEX = -1
    }

    override fun findSignedTransaction(
        id: String,
        transactionStatus: TransactionStatus
    ): Pair<SignedTransactionContainer?, String?> {
        return entityManagerFactory.transaction { em ->
            findSignedTransaction(id, transactionStatus, em)
        }
    }

    private fun findSignedTransaction(
        id: String,
        transactionStatus: TransactionStatus,
        em: EntityManager,
    ): Pair<SignedTransactionContainer?, String?> {
        val (status, isFiltered) = repository.findTransactionStatus(em, id) ?: return null to null
        // VERIFIED can exist with is_filtered = true when there is only a filtered transaction
        // UNVERIFIED can exist with is_filtered = true when there is a unverified signed and filtered transaction
        // DRAFT cannot exist with is_filtered = true
        // INVALID filtered transaction cannot exist
        if (status == TransactionStatus.VERIFIED.value && isFiltered) {
            return null to null
        }
        return if (status == transactionStatus.value) {
            repository.findTransaction(em, id)
                ?: throw InconsistentLedgerStateException("Transaction $id in status $transactionStatus has disappeared from the database")
        } else {
            null
        } to status
    }

    override fun findFilteredTransactionsAndSignatures(
        stateRefs: List<StateRef>,
    ): Map<SecureHash, Pair<FilteredTransaction?, List<DigitalSignatureAndMetadata>>> {
        val txIdToIndexesMap = stateRefs.groupBy { it.transactionId }
            .mapValues { (_, stateRefs) -> stateRefs.map { stateRef -> stateRef.index } }

        // create payload map and make values null by default, if the value of certain filtered tx is null at the end,
        // it means it's not found. The error will be handled in flow side.
        val txIdToFilteredTxAndSignature: MutableMap<SecureHash, Pair<FilteredTransaction?, List<DigitalSignatureAndMetadata>>> = stateRefs
            .groupBy { it.transactionId }
            .mapValues { (_, _) -> null to emptyList<DigitalSignatureAndMetadata>() }.toMutableMap()

        return entityManagerFactory.transaction { em ->
            txIdToIndexesMap.keys.forEach { transactionId ->

                require(txIdToFilteredTxAndSignature.containsKey(transactionId)) { "transaction Id $transactionId is not found." }

                val signedTransactionContainer = findSignedTransaction(transactionId.toString(), TransactionStatus.VERIFIED, em).first
                val wireTransaction = signedTransactionContainer?.wireTransaction
                val signatures = signedTransactionContainer?.signatures ?: emptyList()
                val indexesOfTxId = requireNotNull(txIdToIndexesMap[transactionId])

                if (wireTransaction != null) {
                    if (wireTransaction.id != transactionId) {
                        return@forEach
                    }

                    /** filter wire transaction that is equivalent to:
                     * var filteredTxBuilder = filteredTransactionBuilder
                     *   .withTimeWindow()
                     *   .withOutputStates(indexesOfTxId)
                     *   .withNotary()
                     */
                    val filteredTransaction = filteredTransactionFactory.create(
                        wireTransaction,
                        listOf(
                            ComponentGroupFilterParameters.AuditProof(
                                METADATA.ordinal,
                                TransactionMetadata::class.java,
                                ComponentGroupFilterParameters.AuditProof.AuditProofPredicate.Content { true }
                            ),
                            ComponentGroupFilterParameters.AuditProof(
                                NOTARY.ordinal,
                                Any::class.java,
                                ComponentGroupFilterParameters.AuditProof.AuditProofPredicate.Content { true }
                            ),
                            ComponentGroupFilterParameters.AuditProof(
                                UtxoComponentGroup.OUTPUTS_INFO.ordinal,
                                UtxoOutputInfoComponent::class.java,
                                ComponentGroupFilterParameters.AuditProof.AuditProofPredicate.Index(indexesOfTxId)
                            ),
                            ComponentGroupFilterParameters.AuditProof(
                                UtxoComponentGroup.OUTPUTS.ordinal,
                                ContractState::class.java,
                                ComponentGroupFilterParameters.AuditProof.AuditProofPredicate.Index(indexesOfTxId)
                            )
                        )
                    )
                    txIdToFilteredTxAndSignature[transactionId] = filteredTransaction to signatures
                }
            }
            // filter transaction ids who's filtered tx is null
            val transactionIdsToFind =
                txIdToFilteredTxAndSignature.filter { it.value.toList().contains(null) }.keys.map { it.toString() }

            // find from filtered transaction table if there are still unfounded filtered txs
            val txIdToFilteredTxSignaturePairFromMerkleTable =
                if (transactionIdsToFind.isNotEmpty()) {
                    findFilteredTransactions(transactionIdsToFind, em)
                } else {
                    emptyMap()
                }
            (txIdToFilteredTxAndSignature + txIdToFilteredTxSignaturePairFromMerkleTable).toMap()
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
            val (status, isFiltered) = repository.findTransactionStatus(em, id) ?: return null to null
            // VERIFIED can exist with is_filtered = true when there is only a filtered transaction
            // UNVERIFIED can exist with is_filtered = true when there is a unverified signed and filtered transaction
            // DRAFT cannot exist with is_filtered = true
            // INVALID filtered transaction cannot exist
            if (status == TransactionStatus.VERIFIED.value && isFiltered) {
                return null to null
            }
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

    private fun hash(data: ByteArray) = sandboxDigestService.hash(data, DigestAlgorithmName.SHA2_256).toString()

    override fun persistTransaction(transaction: UtxoTransactionReader, utxoTokenMap: Map<StateRef, UtxoToken>): List<CordaPackageSummary> {
        return persistTransaction(transaction, utxoTokenMap) { block ->
            entityManagerFactory.transaction { em -> block(em) }
        }
    }

    override fun persistTransactionIfDoesNotExist(transaction: UtxoTransactionReader): Pair<String?, List<CordaPackageSummary>> {
        entityManagerFactory.transaction { em ->
            val transactionIdString = transaction.id.toString()
            val (status, isFiltered) = repository.findTransactionStatus(em, transactionIdString) ?: run {
                return null to persistTransaction(transaction, emptyMap()) { block -> block(em) }
            }
            // VERIFIED can exist with is_filtered = true when there is only a filtered transaction
            // UNVERIFIED can exist with is_filtered = true when there is a unverified signed and filtered transaction
            // DRAFT cannot exist with is_filtered = true
            // INVALID filtered transaction cannot exist
            if (status == TransactionStatus.VERIFIED.value && isFiltered) {
                return null to emptyList()
            }
            return status to emptyList()
        }
    }

    private inline fun persistTransaction(
        transaction: UtxoTransactionReader,
        utxoTokenMap: Map<StateRef, UtxoToken>,
        optionalTransactionBlock: ((EntityManager) -> Unit) -> Unit
    ): List<CordaPackageSummary> {
        val nowUtc = utcClock.instant()
        val transactionIdString = transaction.id.toString()

        val metadataBytes = transaction.rawGroupLists[0][0]
        val metadataHash = sandboxDigestService.hash(metadataBytes, DigestAlgorithmName.SHA2_256).toString()

        val metadata = transaction.metadata

        val visibleTransactionOutputs = transaction.getVisibleStates().entries.map { (stateIndex, stateAndRef) ->
            UtxoRepository.VisibleTransactionOutput(
                stateIndex,
                stateAndRef.state.contractState::class.java.canonicalName,
                CustomRepresentation(extractJsonDataFromState(stateAndRef)),
                utxoTokenMap[stateAndRef.ref],
                stateAndRef.state.notaryName.toString()
            )
        }

        val transactionSignatures = transaction.signatures.mapIndexed { index, signature ->
            UtxoRepository.TransactionSignature(
                index,
                serializationService.serialize(signature).bytes,
                signature.by
            )
        }

        optionalTransactionBlock { em ->

            repository.persistTransactionMetadata(
                em,
                metadataHash,
                metadataBytes,
                requireNotNull(metadata.getMembershipGroupParametersHash()) { "Metadata without membership group parameters hash" },
                requireNotNull(metadata.getCpiMetadata()) { "Metadata without CPI metadata" }.fileChecksum
            )

            if (transaction.status != TransactionStatus.UNVERIFIED) {
                // Insert the Transaction
                repository.persistTransaction(
                    em,
                    transactionIdString,
                    transaction.privacySalt.bytes,
                    transaction.account,
                    nowUtc,
                    transaction.status,
                    metadataHash,
                )
            } else {
                repository.persistUnverifiedTransaction(
                    em,
                    transactionIdString,
                    transaction.privacySalt.bytes,
                    transaction.account,
                    nowUtc,
                    metadataHash
                )
            }

            repository.persistTransactionComponents(
                em,
                transactionIdString,
                transaction.rawGroupLists,
                this::hash
            )

            val consumedTransactionSources = transaction.getConsumedStateRefs().mapIndexed { index, input ->
                UtxoRepository.TransactionSource(UtxoComponentGroup.INPUTS, index, input.transactionId.toString(), input.index)
            }

            val referenceTransactionSources = transaction.getReferenceStateRefs().mapIndexed { index, input ->
                UtxoRepository.TransactionSource(UtxoComponentGroup.REFERENCES, index, input.transactionId.toString(), input.index)
            }

            repository.persistTransactionSources(em, transactionIdString, consumedTransactionSources + referenceTransactionSources)

            // Mark inputs as consumed
            if (transaction.status == TransactionStatus.VERIFIED) {
                repository.persistVisibleTransactionOutputs(
                    em,
                    transactionIdString,
                    nowUtc,
                    visibleTransactionOutputs
                )

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
            repository.persistTransactionSignatures(
                em,
                transactionIdString,
                transactionSignatures,
                nowUtc
            )
        }

        return emptyList()
    }

    override fun persistTransactionSignatures(id: String, signatures: List<ByteArray>, startingIndex: Int) {
        val transactionSignatures = signatures.mapIndexed { index, bytes ->
            val signature = serializationService.deserialize<DigitalSignatureAndMetadata>(bytes)
            UtxoRepository.TransactionSignature(
                startingIndex + index,
                serializationService.serialize(signature).bytes,
                signature.by
            )
        }

        entityManagerFactory.transaction { em ->
            repository.persistTransactionSignatures(
                em,
                id,
                transactionSignatures,
                utcClock.instant()
            )
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
        filteredTransactionsAndSignatures: Map<FilteredTransaction, List<DigitalSignatureAndMetadata>>,
        account: String
    ) {
        entityManagerFactory.transaction { em ->

            filteredTransactionsAndSignatures.forEach { (filteredTransaction, signatures) ->

                val nowUtc = utcClock.instant()

                // 1. Get the metadata bytes from the 0th component group merkle proof and create the hash
                val metadataBytes = filteredTransaction.filteredComponentGroups[0]
                    ?.merkleProof
                    ?.leaves
                    ?.get(0)
                    ?.leafData

                requireNotNull(metadataBytes) {
                    "Could not find metadata in the filtered transaction with id: ${filteredTransaction.id}"
                }

                val metadata = filteredTransaction.metadata as TransactionMetadataInternal

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
                repository.persistFilteredTransaction(
                    em,
                    filteredTransaction.id.toString(),
                    filteredTransaction.privacySalt.bytes,
                    account,
                    nowUtc,
                    metadataHash,
                )

                // 4. Persist the signatures
                repository.persistTransactionSignatures(
                    em,
                    filteredTransaction.id.toString(),
                    signatures.mapIndexed { index, signature ->
                        UtxoRepository.TransactionSignature(
                            index,
                            serializationService.serialize(signature).bytes,
                            signature.by
                        )
                    },
                    nowUtc
                )

                val startTime = System.nanoTime()

                val topLevelTransactionMerkleProof = createTransactionMerkleProof(
                    filteredTransaction.id.toString(),
                    TOP_LEVEL_MERKLE_PROOF_GROUP_INDEX,
                    filteredTransaction.topLevelMerkleProof.treeSize,
                    filteredTransaction.topLevelMerkleProof.leaves.map { it.index },
                    filteredTransaction.topLevelMerkleProof.hashes.map { it.toString() }
                )

                val componentGroupTransactionMerkleProofs = filteredTransaction.filteredComponentGroups.map { (groupIndex, groupData) ->
                    val proof = createTransactionMerkleProof(
                        filteredTransaction.id.toString(),
                        groupIndex,
                        groupData.merkleProof.treeSize,
                        groupData.merkleProof.leaves.map { it.index },
                        groupData.merkleProof.hashes.map { it.toString() }
                    )
                    val leaves = groupData.merkleProof.leaves.map { leaf ->
                        UtxoRepository.TransactionMerkleProofLeaf(
                            proof.merkleProofId,
                            leaf.index
                        )
                    }
                    val components = groupData.merkleProof.leaves.map { leaf ->
                        UtxoRepository.TransactionComponent(
                            filteredTransaction.id.toString(),
                            groupIndex,
                            leaf.index,
                            leaf.leafData
                        )
                    }
                    TransactionMerkleProofToPersist(proof, leaves, components)
                }

                CordaMetrics.Metric.Ledger.FilteredTransactionComputationTime
                    .builder()
                    .build()
                    .record(Duration.ofNanos(System.nanoTime() - startTime))

                // 6. Persist the top level and component group merkle proofs
                // No need to persist the leaf data for the top level merkle proof as we can reconstruct that
                repository.persistMerkleProofs(
                    em,
                    listOf(topLevelTransactionMerkleProof) + componentGroupTransactionMerkleProofs.map { it.proof }
                )

                // 7. Persist the leaf data for each component group
                repository.persistMerkleProofLeaves(em, componentGroupTransactionMerkleProofs.map { it.leaves }.flatten())
                repository.persistTransactionComponents(
                    em,
                    componentGroupTransactionMerkleProofs.map { it.components }.flatten(),
                    this::hash
                )
            }
        }
    }

    @VisibleForTesting
    internal fun findFilteredTransactions(
        transactionIds: List<String>
    ): Map<SecureHash, Pair<FilteredTransaction?, List<DigitalSignatureAndMetadata>>> {
        return entityManagerFactory.transaction { em -> findFilteredTransactions(transactionIds, em) }
    }

    private fun findFilteredTransactions(
        transactionIds: List<String>,
        em: EntityManager
    ): Map<SecureHash, Pair<FilteredTransaction?, List<DigitalSignatureAndMetadata>>> {
        return repository.findFilteredTransactions(em, transactionIds)
            .map { (transactionId, ftxDto) ->
                // Map through each found transaction

                val nullOrEmptyField = ftxDto.takeIf {
                    it.topLevelMerkleProofs.isEmpty() || it.componentMerkleProofMap.isEmpty() || it.privacySalt == null ||
                        it.metadataBytes == null || it.signatures.isEmpty()
                }

                // If any of the fields in dto are empty or null, skip to next iteration since we can't create filtered transaction.
                if (nullOrEmptyField != null) {
                    log.warn(
                        "The filtered transaction $transactionId is missing data for any of " +
                            "topLevelMerkleProofs = ${ftxDto.topLevelMerkleProofs}, " +
                            "componentMerkleProofMap = ${ftxDto.componentMerkleProofMap}, " +
                            "privacySalt = ${ftxDto.privacySalt}, " +
                            "metadataBytes = ${ftxDto.metadataBytes}, " +
                            "signatures = ${ftxDto.signatures}."
                    )
                    return@map null
                }

                // 1. Parse the metadata bytes
                val filteredTransactionMetadata = parseMetadata(
                    ftxDto.metadataBytes!!,
                    jsonValidator,
                    jsonMarshallingService
                )

                val rootDigestProvider = filteredTransactionMetadata.getRootMerkleTreeDigestProvider(merkleTreeProvider)

                // 2. Merge the Merkle proofs for each component group
                val componentDigestProviders = mutableMapOf<Int, MerkleTreeHashDigestProvider>()

                val mergedMerkleProofs = ftxDto.componentMerkleProofMap.mapValues { (componentGroupIndex, merkleProofDtoList) ->
                    val componentGroupHashDigestProvider = filteredTransactionMetadata.getComponentGroupMerkleTreeDigestProvider(
                        ftxDto.privacySalt!!,
                        componentGroupIndex,
                        merkleTreeProvider,
                        digestService
                    )
                    componentDigestProviders[componentGroupIndex] = componentGroupHashDigestProvider
                    merkleProofDtoList.map { merkleProofDto ->
                        // Transform the MerkleProofDto objects to MerkleProof objects
                        // If the merkle proof is metadata, we need to add the bytes because it's not part of the component table
                        val merkleProofDtoOverride = if (merkleProofDto.groupIndex == 0) {
                            merkleProofDto.copy(leavesWithData = mapOf(0 to ftxDto.metadataBytes!!))
                        } else {
                            merkleProofDto
                        }

                        merkleProofDtoOverride.toMerkleProof(
                            merkleProofFactory,
                            componentGroupHashDigestProvider
                        )
                    }.reduce { accumulator, merkleProof ->
                        // Then  keep merging the elements into each other
                        (accumulator as MerkleProofInternal).merge(
                            merkleProof,
                            componentGroupHashDigestProvider
                        )
                    }
                }

                // 3. Calculate the root hash of each component group merkle proof
                val calculatedComponentGroupRootsHashes = mergedMerkleProofs.map { (componentGroupIndex, merkleProof) ->
                    // We don't store the leaf data for top level proofs, so we need to calculate it from the
                    // existing component group proofs
                    // Map through the visible component groups and calculate the root of the given component merkle proof
                    val componentGroupHashDigestProvider = componentDigestProviders[componentGroupIndex]

                    requireNotNull(componentGroupHashDigestProvider) {
                        "Could not find hash digest provider for $componentGroupIndex"
                    }

                    componentGroupIndex to merkleProof.calculateRoot(componentGroupHashDigestProvider)
                }.toMap()

                // 4. Create the top level Merkle proof by merging all the top level merkle proofs together
                val mergedTopLevelProof = ftxDto.topLevelMerkleProofs.map {
                    // Transform the MerkleProofDto objects to MerkleProof objects
                    merkleProofFactory.createAuditMerkleProof(
                        it.treeSize,
                        it.visibleLeaves.associateWith { componentGroupIndex ->

                            // Use the already calculated component group root
                            val componentGroupRootBytes = calculatedComponentGroupRootsHashes[componentGroupIndex]?.bytes

                            // At this point we should have this available
                            requireNotNull(componentGroupRootBytes) {
                                "Could not find merkle proof for component group index: $componentGroupIndex"
                            }

                            componentGroupRootBytes
                        },
                        it.hashes,
                        rootDigestProvider
                    )
                }.reduce { accumulator, merkleProof ->
                    // Then  keep merging the elements into each other
                    (accumulator as MerkleProofInternal).merge(
                        merkleProof,
                        rootDigestProvider
                    )
                }

                // 5. Create the filtered transaction object
                val filteredTransaction = filteredTransactionFactory.create(
                    parseSecureHash(transactionId),
                    mergedTopLevelProof,
                    mergedMerkleProofs.map {
                        it.key to FilteredComponentGroup(it.key, it.value)
                    }.toMap(),
                    ftxDto.privacySalt!!.bytes
                )

                // 6. Map the transaction id to the filtered transaction object and signatures
                filteredTransaction.id to Pair(filteredTransaction, ftxDto.signatures)
            }.filterNotNull().toMap()
    }

    fun createTransactionMerkleProof(
        transactionId: String,
        groupIndex: Int,
        treeSize: Int,
        leafIndexes: List<Int>,
        leafHashes: List<String>
    ): UtxoRepository.TransactionMerkleProof {
        return UtxoRepository.TransactionMerkleProof(
            digestService.hash(
                "$transactionId;$groupIndex;${leafIndexes.joinToString(separator = ",")}".toByteArray(Charsets.UTF_8),
                DigestAlgorithmName.SHA2_256
            ).toString(),
            transactionId,
            groupIndex,
            treeSize,
            leafIndexes,
            leafHashes
        )
    }

    private data class TransactionMerkleProofToPersist(
        val proof: UtxoRepository.TransactionMerkleProof,
        val leaves: List<UtxoRepository.TransactionMerkleProofLeaf>,
        val components: List<UtxoRepository.TransactionComponent>
    )
}
