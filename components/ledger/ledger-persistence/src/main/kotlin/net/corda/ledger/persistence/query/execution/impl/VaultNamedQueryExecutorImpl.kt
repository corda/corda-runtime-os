package net.corda.ledger.persistence.query.execution.impl

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.persistence.EntityResponse
import net.corda.data.persistence.FindWithNamedQuery
import net.corda.ledger.persistence.common.ComponentLeafDto
import net.corda.ledger.persistence.query.data.VaultNamedQuery
import net.corda.ledger.persistence.query.execution.VaultNamedQueryExecutor
import net.corda.ledger.persistence.query.registration.VaultNamedQueryRegistry
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.ledger.utxo.data.transaction.UtxoTransactionOutputDto
import net.corda.orm.utils.transaction
import net.corda.persistence.common.exceptions.NullParameterException
import net.corda.utilities.serialization.deserialize
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import javax.persistence.EntityManagerFactory
import javax.persistence.Tuple

class VaultNamedQueryExecutorImpl(
    private val entityManagerFactory: EntityManagerFactory,
    private val registry: VaultNamedQueryRegistry,
    private val serializationService: SerializationService
) : VaultNamedQueryExecutor {

    private companion object {
        const val UTXO_VISIBLE_TX_TABLE = "utxo_visible_transaction_state"
        const val UTXO_TX_COMPONENT_TABLE = "utxo_transaction_component"
        const val TIMESTAMP_LIMIT_PARAM_NAME = "Corda_TimestampLimit"

        val log = LoggerFactory.getLogger(VaultNamedQueryExecutorImpl::class.java)
    }

    override fun executeQuery(
        request: FindWithNamedQuery
    ): EntityResponse {

        log.debug("Executing query: ${request.queryName}")

        // Get the query from the registry and make sure it exists
        val vaultNamedQuery = registry.getQuery(request.queryName)

        require(vaultNamedQuery != null) { "Query with name ${request.queryName} could not be found!" }
        require(vaultNamedQuery.query.type == VaultNamedQuery.Type.WHERE_JSON) {
            "Only WHERE queries are supported for now."
        }

        // Fetch the state and refs for the given transaction IDs
        val contractStateResults = fetchStateAndRefs(
            request,
            vaultNamedQuery.query.query
        )

        // Deserialize the parameters into readable objects instead of bytes
        val deserializedParams = request.parameters.mapValues {
            serializationService.deserialize(it.value.array(), Any::class.java)
        }

        // Apply filters and transforming functions (if there's any)
        val filteredAndTransformedResults = contractStateResults.filter {
            vaultNamedQuery.filter?.filter(it, deserializedParams) ?: true
        }.mapNotNull { // This has no effect as of now, but we keep it for safety purposes
            vaultNamedQuery.mapper?.transform(it, deserializedParams) ?: it
        }

        // Once filtering and transforming are done collector function can be applied (if present)
        val collectedResults = vaultNamedQuery.collector?.collect(
            filteredAndTransformedResults,
            deserializedParams
        )?.results?.filterNotNull() ?: filteredAndTransformedResults

        // Return the filtered/transformed/collected (if present) result to the caller
        return EntityResponse.newBuilder()
            .setResults(collectedResults.map { ByteBuffer.wrap(serializationService.serialize(it).bytes) })
            .setMetadata(KeyValuePairList(listOf(KeyValuePair("numberOfRowsFromQuery", contractStateResults.size.toString()))))
            .build()
    }

    /**
     * A function that fetches the contract states that belong to the given transaction IDs. The data stored in the
     * component table will be deserialized into contract states using component groups.
     */
    private fun fetchStateAndRefs(
        request: FindWithNamedQuery,
        whereJson: String?
    ): List<StateAndRef<ContractState>> {

        validateParameters(request)

        @Suppress("UNCHECKED_CAST")
        val componentGroups = entityManagerFactory.transaction { em ->
                val query = em.createNativeQuery(
                    "SELECT tc.transaction_id, tc.group_idx, tc.leaf_idx, tc.data FROM " +
                            "$UTXO_TX_COMPONENT_TABLE AS tc " +
                            "JOIN $UTXO_VISIBLE_TX_TABLE AS visible_states " +
                                "ON visible_states.transaction_id = tc.transaction_id " +
                                "AND visible_states.leaf_idx = tc.leaf_idx " +
                            "$whereJson " +
                            "AND tc.group_idx IN (:groupIndices) " +
                            "AND visible_states.created <= :$TIMESTAMP_LIMIT_PARAM_NAME " +
                            "ORDER BY tc.created, tc.transaction_id, tc.leaf_idx, tc.group_idx",
                    Tuple::class.java
                )

                request.parameters.filter { it.value != null }.forEach { rec ->
                    val bytes = rec.value.array()
                    query.setParameter(rec.key, serializationService.deserialize(bytes))
                }

            // Setting the parameter here prevents them from being set by CorDapp code.
            query.setParameter(
                "groupIndices",
                listOf(
                    UtxoComponentGroup.OUTPUTS.ordinal,
                    UtxoComponentGroup.OUTPUTS_INFO.ordinal
                )
            )
                // Each transaction will have two rows: outputs/outputs info so we need to multiply offset/result by 2
                query.firstResult = request.offset * 2
                query.maxResults = request.limit * 2

                query.resultList as List<Tuple>
            }.map { t ->
                ComponentLeafDto(
                    t[0] as String, // transactionId
                    (t[1] as Number).toInt(), // groupIndex
                    (t[2] as Number).toInt(), // leafIndex
                    t[3] as ByteArray // data
                )
            }.groupBy { it.groupIndex }

        val outputInfos = componentGroups[UtxoComponentGroup.OUTPUTS_INFO.ordinal]
            ?.associateBy { it.transactionId to it.leafIndex }
            ?: emptyMap()

        return componentGroups[UtxoComponentGroup.OUTPUTS.ordinal]?.map {
            val serializedInfo = outputInfos[it.transactionId to it.leafIndex]

            requireNotNull(serializedInfo) {
                "Missing output info at index [${it.leafIndex}] for UTXO transaction with ID [${it.transactionId}]"
            }

            UtxoTransactionOutputDto(
                it.transactionId,
                it.leafIndex,
                serializedInfo.data,
                it.data
            ).toStateAndRef(serializationService)
        } ?: emptyList()
    }

    private fun validateParameters(request: FindWithNamedQuery) {
        val nullParamNames = request.parameters.filter { it.value == null }.map { it.key }

        if (nullParamNames.isNotEmpty()) {
            val msg = "Null value found for parameters ${nullParamNames.joinToString(", ")}"
            log.error(msg)
            throw NullParameterException(msg)
        }
    }
}
