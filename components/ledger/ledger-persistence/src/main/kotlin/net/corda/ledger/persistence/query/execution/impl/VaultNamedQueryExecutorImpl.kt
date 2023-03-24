package net.corda.ledger.persistence.query.execution.impl

import net.corda.data.identity.HoldingIdentity
import net.corda.data.persistence.EntityResponse
import net.corda.data.persistence.FindWithNamedQuery
import net.corda.ledger.persistence.common.ComponentLeafDto
import net.corda.ledger.persistence.query.execution.VaultNamedQueryExecutor
import net.corda.ledger.persistence.query.registration.VaultNamedQueryRegistry
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.orm.utils.transaction
import net.corda.persistence.common.EntitySandboxService
import net.corda.persistence.common.getEntityManagerFactory
import net.corda.sandbox.type.SandboxConstants
import net.corda.sandbox.type.UsedByPersistence
import net.corda.utilities.serialization.deserialize
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.ledger.utxo.ContractState
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import javax.persistence.Tuple

@Component(
    service = [
        VaultNamedQueryExecutor::class,
        UsedByPersistence::class
    ],
    property = [
        SandboxConstants.CORDA_MARKER_ONLY_SERVICE
    ],
    scope = ServiceScope.PROTOTYPE
)
class VaultNamedQueryExecutorImpl @Activate constructor(
    @Reference(service = EntitySandboxService::class)
    private val entitySandboxService: EntitySandboxService,
    @Reference(service = VaultNamedQueryRegistry::class)
    private val registry: VaultNamedQueryRegistry,
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService,
): VaultNamedQueryExecutor, UsedByPersistence {

    private companion object {
        private const val UTXO_VISIBLE_TX_TABLE = "utxo_visible_transaction_state"
        private const val UTXO_TX_COMPONENT_TABLE = "utxo_transaction_component"

        private val log = LoggerFactory.getLogger(VaultNamedQueryExecutorImpl::class.java)
    }

    override fun executeQuery(
        holdingIdentity: HoldingIdentity,
        request: FindWithNamedQuery
    ): EntityResponse {

        log.debug("Executing query: ${request.queryName}")

        // Get the query from the registry and make sure it exists
        val vaultNamedQuery = registry.getQuery(request.queryName)
        require(vaultNamedQuery != null) { "Query with name ${request.queryName} could not be found!" }

        // Deserialize the parameters into readable objects instead of bytes
        val deserializedParams = request.parameters.mapValues {
            serializationService.deserialize(it.value.array(), Any::class.java)
        }

        // Fetch the transaction IDs for the given request
        val transactionIds = fetchTransactionIdsForRequest(
            request,
            vaultNamedQuery.whereJson,
            deserializedParams,
            holdingIdentity
        )

        // Fetch the contract states for the given transaction IDs
        val contractStateResults = fetchContractStates(transactionIds, holdingIdentity)

        // Apply filters and transforming functions (if there's any)
        val filteredAndTransformedResults = contractStateResults.filter {
            vaultNamedQuery.filter?.filter(it, deserializedParams) ?: true
        }.map {
            vaultNamedQuery.mapper?.transform(it, deserializedParams) ?: it
        }

        // Once filtering and transforming are done collector function can be applied (if present)
        val collectedResults = vaultNamedQuery.collector?.collect(
            filteredAndTransformedResults,
            deserializedParams
        )?.results?.filterNotNull() ?: filteredAndTransformedResults

        // Return the filtered/transformed/collected (if present) result to the caller
        return EntityResponse(
            collectedResults.map { ByteBuffer.wrap(serializationService.serialize(it).bytes) }
        )
    }

    /**
     * A function that fetches the transaction IDs that match the given WHERE clause. These transaction IDs will be
     * used to fetch the contract states later on.
     */
    private fun fetchTransactionIdsForRequest(
        request: FindWithNamedQuery,
        whereJson: String?,
        deserializedParams: Map<String, Any>,
        holdingIdentity: HoldingIdentity,
    ): List<String> {
        return entitySandboxService.get(holdingIdentity.toCorda())
            .getEntityManagerFactory()
            .transaction { em ->
                var query = em.createNativeQuery(
                    "SELECT transaction_id FROM $UTXO_VISIBLE_TX_TABLE " +
                            whereJson
                )

                deserializedParams.forEach {
                    query = query.setParameter(it.key, it.value)
                }

                query.firstResult = request.offset
                query.maxResults = request.limit

                query.resultList
            }.filterNotNull().map { it as String }
    }

    /**
     * A function that fetches the contract states that belong to the given transaction IDs. The data stored in the
     * component table will be deserialized into contract states using component groups.
     */
    private fun fetchContractStates(
        txIds: List<String>,
        holdingIdentity: HoldingIdentity
    ): List<ContractState> {
        @Suppress("UNCHECKED_CAST")
        val componentGroups = entitySandboxService.get(holdingIdentity.toCorda())
            .getEntityManagerFactory()
            .transaction { em ->
                em.createNativeQuery(
                    "SELECT tc.transaction_id, tc.group_idx, tc.leaf_idx, tc.data FROM " +
                            "$UTXO_TX_COMPONENT_TABLE tc " +
                            "WHERE transaction_id IN (:txIds) " +
                            "AND tc.group_idx IN (:outputIndices)",
                    Tuple::class.java
                )
                    .setParameter("txIds", txIds)
                    .setParameter("outputIndices", listOf(UtxoComponentGroup.OUTPUTS.ordinal))
                    .resultList as List<Tuple>
            }.map { t ->
                ComponentLeafDto(
                    t[0] as String, // transactionId
                    (t[1] as Number).toInt(), // groupIndex
                    (t[2] as Number).toInt(), // leafIndex
                    t[3] as ByteArray // data
                )
            }.groupBy { it.groupIndex }

        return componentGroups[UtxoComponentGroup.OUTPUTS.ordinal]?.map {
            // TODO Do we need to check output info?
            serializationService.deserialize(it.data)
        } ?: emptyList()
    }
}
