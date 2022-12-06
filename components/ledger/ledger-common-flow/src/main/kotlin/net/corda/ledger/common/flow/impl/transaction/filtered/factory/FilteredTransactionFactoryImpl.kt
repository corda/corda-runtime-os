package net.corda.ledger.common.flow.impl.transaction.filtered.factory

import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.flow.impl.transaction.filtered.FilteredTransactionImpl
import net.corda.ledger.common.flow.transaction.filtered.FilteredComponentGroup
import net.corda.ledger.common.flow.transaction.filtered.FilteredTransaction
import net.corda.ledger.common.flow.transaction.filtered.factory.ComponentGroupFilterParameters
import net.corda.ledger.common.flow.transaction.filtered.factory.FilteredTransactionFactory
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProviderWithSizeProofSupport
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import java.util.function.Predicate

@Component(service = [FilteredTransactionFactory::class, SingletonSerializeAsToken::class, UsedByFlow::class], scope = PROTOTYPE)
class FilteredTransactionFactoryImpl @Activate constructor(
    @Reference(service = JsonMarshallingService::class)
    private val jsonMarshallingService: JsonMarshallingService,
    @Reference(service = MerkleTreeProvider::class)
    private val merkleTreeProvider: MerkleTreeProvider,
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService
) : FilteredTransactionFactory, SingletonSerializeAsToken, UsedByFlow {

    @Suspendable
    override fun create(
        wireTransaction: WireTransaction,
        componentGroupFilterParameters: List<ComponentGroupFilterParameters>,
        filter: Predicate<Any>
    ): FilteredTransaction {

        requireUniqueComponentGroupIndexes(componentGroupFilterParameters)

        // Guarantees construction of [WireTransaction.rootMerkleTree] as it is lazily constructed.
        val transactionId = wireTransaction.id

        val filteredComponentGroups = componentGroupFilterParameters
            .map { filterComponentGroup(wireTransaction, it, filter) }
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

    @Suppress("NestedBlockDepth")
    private fun filterComponentGroup(
        wireTransaction: WireTransaction,
        parameters: ComponentGroupFilterParameters,
        filter: Predicate<Any>,
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
            is ComponentGroupFilterParameters.AuditProof -> {

                val skipFiltering = componentGroupIndex == 0

                val filteredComponents = componentGroup
                    .mapIndexed { index, component -> index to component }
                    .filter { (_, component) ->
                        skipFiltering || filter.test(
                            serializationService.deserialize(
                                component,
                                parameters.deserializedClass
                            )
                        )
                    }

                wireTransaction.componentMerkleTrees[componentGroupIndex]!!.let { merkleTree ->
                    if (filteredComponents.isEmpty()) {
                        // If the unfiltered component gropup is empty, we return the proof of the marker.
                        if (componentGroup.isEmpty()) {
                            merkleTree.createAuditProof(listOf(0))
                        } else {    //If we filter out everything, we return a size proof.
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

        return FilteredComponentGroup(componentGroupIndex, merkleProof, parameters.merkleProofType)
    }
}