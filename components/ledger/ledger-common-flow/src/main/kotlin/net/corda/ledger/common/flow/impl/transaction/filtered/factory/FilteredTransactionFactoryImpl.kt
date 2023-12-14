package net.corda.ledger.common.flow.impl.transaction.filtered.factory

import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.flow.impl.transaction.filtered.FilteredTransactionImpl
import net.corda.ledger.common.flow.transaction.filtered.FilteredComponentGroup
import net.corda.ledger.common.flow.transaction.filtered.FilteredTransaction
import net.corda.ledger.common.flow.transaction.filtered.factory.ComponentGroupFilterParameters
import net.corda.ledger.common.flow.transaction.filtered.factory.ComponentGroupFilterParameters.AuditProof
import net.corda.ledger.common.flow.transaction.filtered.factory.FilteredTransactionFactory
import net.corda.sandbox.type.SandboxConstants.CORDA_SYSTEM_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProviderWithSizeProofSupport
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceScope.PROTOTYPE_REQUIRED
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(
    service = [ FilteredTransactionFactory::class, SingletonSerializeAsToken::class, UsedByFlow::class ],
    property = [ CORDA_SYSTEM_SERVICE ],
    scope = PROTOTYPE,
)
class FilteredTransactionFactoryImpl @Activate constructor(
    @Reference(service = JsonMarshallingService::class, scope = PROTOTYPE_REQUIRED)
    private val jsonMarshallingService: JsonMarshallingService,
    @Reference(service = MerkleTreeProvider::class)
    private val merkleTreeProvider: MerkleTreeProvider,
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService
) : FilteredTransactionFactory, SingletonSerializeAsToken, UsedByFlow {

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
            jsonMarshallingService,
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

        val componentGroupMerkleTreeDigestProvider = wireTransaction.getComponentGroupMerkleTreeDigestProvider(
            wireTransaction.privacySalt,
            componentGroupIndex
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
