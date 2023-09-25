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

        const val RESULT_SET_FILL_RETRY_LIMIT = 5

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

        // Deserialize the parameters into readable objects instead of bytes
        val deserializedParams = request.parameters.mapValues {
            serializationService.deserialize(it.value.array(), Any::class.java)
        }

        // Fetch and filter the results and try to fill up the page size then map the results
        // mapNotNull has no effect as of now, but we keep it for safety purposes
        val (fetchedRecords, numberOfRowsFromQuery) = filterResultsAndFillPageSize(
            request,
            vaultNamedQuery,
            deserializedParams
        )

        val filteredAndTransformedResults = fetchedRecords.mapNotNull {
            vaultNamedQuery.mapper?.transform(it, deserializedParams) ?: it
        }

        // Once filtering and transforming are done collector function can be applied (if present)
        val collectedResults = vaultNamedQuery.collector?.collect(
            filteredAndTransformedResults,
            deserializedParams
        )?.results?.filterNotNull() ?: filteredAndTransformedResults

        // Return the filtered/transformed/collected (if present) result and the offset to continue the paging from to the caller
        return EntityResponse.newBuilder()
            .setResults(collectedResults.map { ByteBuffer.wrap(serializationService.serialize(it).bytes) })
            .setMetadata(KeyValuePairList(listOf(
                KeyValuePair("numberOfRowsFromQuery", numberOfRowsFromQuery.toString())
            ))).build()
    }

    /**
     * This function will fetch a given number ("page size") of records from the database
     * (amount defined by [request]'s limit field).
     *
     * After fetching those records the in-memory filter will be applied. If the filtering
     * reduces the amount of records below the "page size", then another "page size" number
     * of records will be fetched and filtered.
     *
     * This logic will be repeated until either:
     * - The size of the filtered results reaches the "page size"
     * - We run out of data to fetch from the database
     * - We reach the number of retries ([RESULT_SET_FILL_RETRY_LIMIT])
     *
     * If any of these conditions happen, we just return the result set as-is without filling
     * up the "page size".
     *
     * Will return a pair of the fetched and "filtered" results from the database and the offset
     * that the paging can be continued from.
     */
    private fun filterResultsAndFillPageSize(
        request: FindWithNamedQuery,
        vaultNamedQuery: VaultNamedQuery,
        deserializedParams: Map<String, Any>
    ): FilterResult {
        val filteredResults = mutableListOf<StateAndRef<ContractState>>()

        var currentRetry = 0
        var numberOfRowsFromQuery = 0

        while (filteredResults.size < request.limit && currentRetry < RESULT_SET_FILL_RETRY_LIMIT) {
            ++currentRetry

            log.trace("Executing try: $currentRetry, fetched ${filteredResults.size} number of results so far.")

            // Fetch the state and refs for the given transaction IDs
            val contractStateResults = fetchStateAndRefs(
                request,
                vaultNamedQuery.query.query,
                offset = request.offset + numberOfRowsFromQuery
            )

            // If we have no filter, there's no need to continue the loop
            if (vaultNamedQuery.filter == null) {
                return FilterResult(
                    results = contractStateResults,
                    numberOfRowsFromQuery = contractStateResults.size
                )
            }

            // If we can't fetch more states we just return the result set as-is
            if (contractStateResults.isEmpty()) {
                break
            }

            contractStateResults.forEach { contractStateResult ->
                ++numberOfRowsFromQuery
                if (vaultNamedQuery.filter.filter(contractStateResult, deserializedParams)) {
                    filteredResults.add(contractStateResult)
                }

                if (filteredResults.size >= request.limit) {
                    return FilterResult(
                        results = filteredResults,
                        numberOfRowsFromQuery = numberOfRowsFromQuery
                    )
                }
            }
        }

        return FilterResult(
            results = filteredResults,
            numberOfRowsFromQuery = numberOfRowsFromQuery
        )
    }

    /**
     * A function that fetches the contract states that belong to the given transaction IDs. The data stored in the
     * component table will be deserialized into contract states using component groups.
     */
    private fun fetchStateAndRefs(
        request: FindWithNamedQuery,
        whereJson: String?,
        offset: Int
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
                        WHERE ($whereJson)
                        AND visible_states.created <= :$TIMESTAMP_LIMIT_PARAM_NAME
                        ORDER BY tc_output.created, tc_output.transaction_id, tc_output.leaf_idx
                """,
                Tuple::class.java
            )

            request.parameters.filter { it.value != null }.forEach { rec ->
                val bytes = rec.value.array()
                query.setParameter(rec.key, serializationService.deserialize(bytes))
            }

            query.firstResult = offset
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

    private data class FilterResult(
        val results: List<StateAndRef<ContractState>>,
        val numberOfRowsFromQuery: Int
    )
}
