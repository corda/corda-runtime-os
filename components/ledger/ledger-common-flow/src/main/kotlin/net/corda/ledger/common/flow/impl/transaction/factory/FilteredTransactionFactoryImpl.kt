package net.corda.ledger.common.flow.impl.transaction.factory

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.data.transaction.filtered.FilteredComponentGroup
import net.corda.ledger.common.data.transaction.filtered.FilteredTransaction
import net.corda.ledger.common.data.transaction.filtered.FilteredTransactionImpl
import net.corda.ledger.common.flow.transaction.factory.ComponentGroupFilterParameters
import net.corda.ledger.common.flow.transaction.factory.FilteredTransactionFactory
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProviderWithSizeProofSupport
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import java.util.function.Predicate

@Component(service = [FilteredTransactionFactory::class, SingletonSerializeAsToken::class], scope = PROTOTYPE)
class FilteredTransactionFactoryImpl @Activate constructor(
    @Reference(service = JsonMarshallingService::class)
    private val jsonMarshallingService: JsonMarshallingService,
    @Reference(service = MerkleTreeProvider::class)
    private val merkleTreeProvider: MerkleTreeProvider,
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService
) : FilteredTransactionFactory, SingletonSerializeAsToken {

    @Suspendable
    override fun create(
        wireTransaction: WireTransaction,
        componentGroupFilterParameters: List<ComponentGroupFilterParameters>,
        filter: Predicate<Any>
    ): FilteredTransaction {

        requireUniqueComponentGroupOrdinals(componentGroupFilterParameters)

        // Guarantees construction of [WireTransaction.rootMerkleTree] as it is lazily constructed.
        val transactionId = wireTransaction.id

        val filteredComponentGroups = componentGroupFilterParameters
            .map { filterComponentGroup(wireTransaction, it, filter) }
            .associateBy(FilteredComponentGroup::componentGroupOrdinal)

        return FilteredTransactionImpl(
            id = transactionId,
            componentGroupMerkleProof = wireTransaction.rootMerkleTree.createAuditProof(filteredComponentGroups.keys.toList()),
            filteredComponentGroups,
            jsonMarshallingService,
            merkleTreeProvider
        )
    }

    private fun requireUniqueComponentGroupOrdinals(componentGroupFilterParameters: List<ComponentGroupFilterParameters>) {
        val componentGroupOrdinals = componentGroupFilterParameters.map { it.componentGroupOrdinal }
        require(componentGroupFilterParameters.size == componentGroupOrdinals.toSet().size) {
            "Unique component group indexes are required when filtering a transaction, indexes: $componentGroupOrdinals"
        }
    }

    private fun filterComponentGroup(
        wireTransaction: WireTransaction,
        parameters: ComponentGroupFilterParameters,
        filter: Predicate<Any>,
    ): FilteredComponentGroup {

        val componentGroupOrdinal = parameters.componentGroupOrdinal
        val componentGroup = wireTransaction.getComponentGroupList(componentGroupOrdinal)

        val componentGroupMerkleTreeDigestProvider = wireTransaction.getComponentGroupMerkleTreeDigestProvider(
            wireTransaction.privacySalt,
            componentGroupOrdinal
        )

        val merkleProof = when (parameters) {
            is ComponentGroupFilterParameters.AuditProof -> {

                val skipFiltering = componentGroupOrdinal == 0

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

                wireTransaction.componentMerkleTrees[componentGroupOrdinal]!!.let { merkleTree ->
                    if (filteredComponents.isEmpty()) {
                        merkleTree.createAuditProof(listOf(0))
                    } else {
                        merkleTree.createAuditProof(filteredComponents.map { (index, _) -> index })
                    }
                }
            }
            is ComponentGroupFilterParameters.SizeProof -> {

                wireTransaction.componentMerkleTrees[componentGroupOrdinal]!!.let { merkleTree ->
                    if (wireTransaction.getComponentGroupList(componentGroupOrdinal).isEmpty()) {
                        merkleTree.createAuditProof(listOf(0))
                    } else {
                        val componentGroupMerkleTreeSizeProofProvider =
                            checkNotNull(componentGroupMerkleTreeDigestProvider as? MerkleTreeHashDigestProviderWithSizeProofSupport) {
                                "Expected to have digest provider with size proof support"
                            }
                        componentGroupMerkleTreeSizeProofProvider.getSizeProof(merkleTree.leaves)
                    }
                }
            }
        }

        return FilteredComponentGroup(componentGroupOrdinal, merkleProof, parameters.merkleProofType)
    }
}