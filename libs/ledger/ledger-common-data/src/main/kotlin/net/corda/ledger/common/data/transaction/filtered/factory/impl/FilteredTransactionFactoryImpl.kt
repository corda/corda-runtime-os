package net.corda.ledger.common.data.transaction.filtered.factory.impl

import net.corda.common.json.validation.JsonValidator
import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.ledger.common.data.transaction.PrivacySaltImpl
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.data.transaction.filtered.ComponentGroupFilterParameters
import net.corda.ledger.common.data.transaction.filtered.ComponentGroupFilterParameters.AuditProof
import net.corda.ledger.common.data.transaction.filtered.FilteredComponentGroup
import net.corda.ledger.common.data.transaction.filtered.FilteredTransaction
import net.corda.ledger.common.data.transaction.filtered.factory.FilteredTransactionFactory
import net.corda.ledger.common.data.transaction.filtered.impl.FilteredTransactionImpl
import net.corda.ledger.common.data.transaction.getComponentGroupMerkleTreeDigestProvider
import net.corda.sandbox.type.SandboxConstants.CORDA_MARKER_ONLY_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandbox.type.UsedByPersistence
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProviderWithSizeProofSupport
import net.corda.v5.crypto.merkle.MerkleProof
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceScope.PROTOTYPE_REQUIRED
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(
    service = [ FilteredTransactionFactory::class, SingletonSerializeAsToken::class, UsedByFlow::class, UsedByPersistence::class ],
    property = [ CORDA_MARKER_ONLY_SERVICE ],
    scope = PROTOTYPE,
)
class FilteredTransactionFactoryImpl @Activate constructor(
    @Reference(service = JsonMarshallingService::class, scope = PROTOTYPE_REQUIRED)
    private val jsonMarshallingService: JsonMarshallingService,
    @Reference(service = JsonValidator::class)
    private val jsonValidator: JsonValidator,
    @Reference(service = MerkleTreeProvider::class)
    private val merkleTreeProvider: MerkleTreeProvider,
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService,
    @Reference(service = DigestService::class)
    private val digestService: DigestService
) : FilteredTransactionFactory, SingletonSerializeAsToken, UsedByFlow, UsedByPersistence {

    @Suspendable
    override fun create(
        wireTransaction: WireTransaction,
        componentGroupFilterParameters: List<ComponentGroupFilterParameters>
    ): FilteredTransaction {
        requireUniqueComponentGroupIndexes(componentGroupFilterParameters)

        // Guarantees construction of [WireTransaction.rootMerkleTree] as it is lazily constructed.
        val transactionId = wireTransaction.id

        val filteredComponentGroups = componentGroupFilterParameters
            .map { filterComponentGroup(wireTransaction, it) }
            .associateBy(FilteredComponentGroup::componentGroupIndex)

        return FilteredTransactionImpl(
            id = transactionId,
            topLevelMerkleProof = wireTransaction.rootMerkleTree.createAuditProof(filteredComponentGroups.keys.toList()),
            filteredComponentGroups,
            wireTransaction.privacySalt,
            jsonMarshallingService,
            jsonValidator,
            merkleTreeProvider
        )
    }

    @Suspendable
    override fun create(
        transactionId: SecureHash,
        topLevelMerkleProof: MerkleProof,
        filteredComponentGroups: Map<Int, FilteredComponentGroup>,
        privacySaltBytes: ByteArray
    ): FilteredTransaction {
        return FilteredTransactionImpl(
            id = transactionId,
            topLevelMerkleProof = topLevelMerkleProof,
            filteredComponentGroups,
            PrivacySaltImpl(privacySaltBytes),
            jsonMarshallingService,
            jsonValidator,
            merkleTreeProvider
        )
    }

    private fun requireUniqueComponentGroupIndexes(componentGroupFilterParameters: List<ComponentGroupFilterParameters>) {
        val componentGroupIndexes = componentGroupFilterParameters.map { it.componentGroupIndex }
        require(componentGroupFilterParameters.size == componentGroupIndexes.toSet().size) {
            "Unique component group indexes are required when filtering a transaction, indexes: $componentGroupIndexes"
        }
    }

    @Suppress("NestedBlockDepth", "UNCHECKED_CAST")
    private fun filterComponentGroup(
        wireTransaction: WireTransaction,
        parameters: ComponentGroupFilterParameters
    ): FilteredComponentGroup {
        val componentGroupIndex = parameters.componentGroupIndex
        val componentGroup = wireTransaction.getComponentGroupList(componentGroupIndex)

        val componentGroupMerkleTreeDigestProvider = wireTransaction.metadata.getComponentGroupMerkleTreeDigestProvider(
            wireTransaction.privacySalt,
            componentGroupIndex,
            merkleTreeProvider,
            digestService
        )
        val componentGroupMerkleTreeSizeProofProvider =
            checkNotNull(componentGroupMerkleTreeDigestProvider as? MerkleTreeHashDigestProviderWithSizeProofSupport) {
                "Expected to have digest provider with size proof support"
            }

        val merkleProof = when (parameters) {
            is AuditProof<*> -> {
                val skipFiltering = componentGroupIndex == 0

                val filteredComponents = componentGroup
                    .mapIndexed { index, component -> index to component }
                    .filter { (index, component) ->
                        if (skipFiltering) {
                            true
                        } else {
                            when (val predicate = parameters.predicate) {
                                is AuditProof.AuditProofPredicate.Content -> {
                                    (predicate as AuditProof.AuditProofPredicate.Content<Any>).test(
                                        serializationService.deserialize(
                                            component,
                                            parameters.deserializedClass
                                        )
                                    )
                                }
                                is AuditProof.AuditProofPredicate.Index -> {
                                    predicate.test(index)
                                }
                            }
                        }
                    }
                wireTransaction.componentMerkleTrees[componentGroupIndex]!!.let { merkleTree ->
                    if (filteredComponents.isEmpty()) {
                        if (componentGroup.isEmpty()) {
                            // If the unfiltered component group is empty, we return the proof of the marker.
                            merkleTree.createAuditProof(listOf(0))
                        } else {
                            // If we filter out everything, we return a size proof.
                            componentGroupMerkleTreeSizeProofProvider.getSizeProof(merkleTree.leaves)
                        }
                    } else {
                        merkleTree.createAuditProof(filteredComponents.map { it.first })
                    }
                }
            }
            is ComponentGroupFilterParameters.SizeProof -> {
                wireTransaction.componentMerkleTrees[componentGroupIndex]!!.let { merkleTree ->
                    if (wireTransaction.getComponentGroupList(componentGroupIndex).isEmpty()) {
                        merkleTree.createAuditProof(listOf(0))
                    } else {
                        componentGroupMerkleTreeSizeProofProvider.getSizeProof(merkleTree.leaves)
                    }
                }
            }
        }

        return FilteredComponentGroup(componentGroupIndex, merkleProof)
    }
}
