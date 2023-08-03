package net.corda.ledger.persistence.query.execution.impl

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.persistence.EntityResponse
import net.corda.data.persistence.FindWithNamedQuery
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
        return entityManagerFactory.transaction { em ->

            val query = em.createNativeQuery(
                """
                    SELECT tc_output.transaction_id,
                        tc_output.leaf_idx,
                        tc_output_info.data as output_info_data,
                        tc_output.data AS output_data
                        FROM $UTXO_VISIBLE_TX_TABLE AS visible_states
                        JOIN $UTXO_TX_COMPONENT_TABLE AS tc_output_info
                             ON tc_output_info.transaction_id = visible_states.transaction_id
                             AND tc_output_info.leaf_idx = visible_states.leaf_idx
                             AND tc_output_info.group_idx = ${UtxoComponentGroup.OUTPUTS_INFO.ordinal}
                        JOIN $UTXO_TX_COMPONENT_TABLE AS tc_output
                             ON tc_output_info.transaction_id = tc_output.transaction_id
                             AND tc_output_info.leaf_idx = tc_output.leaf_idx
                             AND tc_output.group_idx = ${UtxoComponentGroup.OUTPUTS.ordinal}
                        $whereJson
                        AND visible_states.created <= :$TIMESTAMP_LIMIT_PARAM_NAME
                        ORDER BY tc_output.created, tc_output.transaction_id, tc_output.leaf_idx
                """,
                Tuple::class.java
            )

            request.parameters.filter { it.value != null }.forEach { rec ->
                val bytes = rec.value.array()
                query.setParameter(rec.key, serializationService.deserialize(bytes))
            }

            query.firstResult = request.offset
            query.maxResults = request.limit

            query.resultList as List<Tuple>
        }.map { t ->
            UtxoTransactionOutputDto(
                t[0] as String, // transactionId
                t[1] as Int, // leaf ID
                t[2] as ByteArray, // outputs info data
                t[3] as ByteArray // outputs data
            ).toStateAndRef(serializationService)
        }
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
