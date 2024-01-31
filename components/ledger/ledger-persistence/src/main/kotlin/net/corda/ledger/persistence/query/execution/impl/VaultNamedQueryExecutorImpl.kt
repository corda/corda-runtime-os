package net.corda.ledger.persistence.query.execution.impl

import net.corda.data.KeyValuePairList
import net.corda.data.persistence.EntityResponse
import net.corda.data.persistence.FindWithNamedQuery
import net.corda.ledger.persistence.query.data.VaultNamedQuery
import net.corda.ledger.persistence.query.execution.VaultNamedQueryExecutor
import net.corda.ledger.persistence.query.registration.VaultNamedQueryRegistry
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.ledger.utxo.data.transaction.UtxoVisibleTransactionOutputDto
import net.corda.orm.utils.transaction
import net.corda.utilities.debug
import net.corda.utilities.serialization.deserialize
import net.corda.utilities.trace
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.sql.Timestamp
import java.time.Instant
import javax.persistence.EntityManagerFactory
import javax.persistence.Tuple

class VaultNamedQueryExecutorImpl(
    private val entityManagerFactory: EntityManagerFactory,
    private val registry: VaultNamedQueryRegistry,
    private val serializationService: SerializationService
) : VaultNamedQueryExecutor {

    private companion object {
        const val UTXO_VISIBLE_TX_TABLE = "utxo_visible_transaction_output"
        const val UTXO_TX_COMPONENT_TABLE = "utxo_transaction_component"
        const val UTXO_TX_TABLE = "utxo_transaction"
        const val TIMESTAMP_LIMIT_PARAM_NAME = "Corda_TimestampLimit"

        const val RESULT_SET_FILL_RETRY_LIMIT = 5

        val log = LoggerFactory.getLogger(VaultNamedQueryExecutorImpl::class.java)
    }

    /*
     * Captures data passed back and forth between this query execution and the caller in a flow
     * processor to enable subsequent pages to know where to resume from. Data is opaque outside
     * this class.
     *
     * This class is not part of the corda-api data module because it is not exposed outside of the
     * internal query API.
     */
    @CordaSerializable
    data class ResumePoint(
        val created: Instant,
        val txId: String,
        val leafIdx: Int
    )

    /*
     * Stores query results following processing / filtering, in a form ready to return to the
     * caller.
     */
    private data class ProcessedQueryResults(
        val results: List<StateAndRef<ContractState>>,
        val resumePoint: ResumePoint?,
        val numberOfRowsFromQuery: Int
    )

    /*
     * Stores the raw query data retrieved from an SQL query row.
     */
    private inner class RawQueryData(sqlRow: Tuple) {

        private val txId = sqlRow[0] as String
        private val leafIdx = sqlRow[1] as Int
        private val outputInfoData = sqlRow[2] as ByteArray
        private val outputData = sqlRow[3] as ByteArray
        private val created = (sqlRow[4] as Timestamp).toInstant()

        val stateAndRef: StateAndRef<ContractState> by lazy {
            UtxoVisibleTransactionOutputDto(txId, leafIdx, outputInfoData, outputData)
                .toStateAndRef(serializationService)
        }

        val resumePoint: ResumePoint? by lazy {
            created?.let { ResumePoint(created, txId, leafIdx) }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as RawQueryData

            if (txId != other.txId) return false
            if (leafIdx != other.leafIdx) return false
            if (created != other.created) return false

            return true
        }

        override fun hashCode(): Int {
            var result = txId.hashCode()
            result = 31 * result + leafIdx
            result = 31 * result + (created?.hashCode() ?: 0)
            return result
        }
    }

    /*
     * Stores a set of raw query data returned from a single database query invocation. To support
     * paging, this not only returns the raw query data, but also a `hasMore` flag to indicate
     * whether another page of data is available.
     */
    private data class RawQueryResults(
        val results: List<RawQueryData>,
        val hasMore: Boolean
    )

    override fun executeQuery(
        request: FindWithNamedQuery
    ): EntityResponse {
        log.debug { "Executing query: ${request.queryName}" }

        // Get the query from the registry and make sure it exists
        val vaultNamedQuery = registry.getQuery(request.queryName)

        require(vaultNamedQuery != null) { "Query with name ${request.queryName} could not be found!" }
        require(vaultNamedQuery.query.type == VaultNamedQuery.Type.WHERE_JSON) {
            "Only WHERE queries are supported for now."
        }

        // Deserialize the parameters into readable objects instead of bytes
        val deserializedParams = request.parameters.mapValues { (_, param) ->
            param?.let { serializationService.deserialize<Any>(it.array()) }
        }

        // Fetch and filter the results and try to fill up the page size then map the results
        // mapNotNull has no effect as of now, but we keep it for safety purposes
        val (fetchedRecords, resumePoint, numberOfRowsReturned) = filterResultsAndFillPageSize(
            request,
            vaultNamedQuery,
            deserializedParams
        )

        log.trace {
            "Fetched ${fetchedRecords.size} records in this page " +
                "(${numberOfRowsReturned - fetchedRecords.size} records filtered)"
        }

        val filteredAndTransformedResults = fetchedRecords.mapNotNull {
            vaultNamedQuery.mapper?.transform(it, deserializedParams) ?: it
        }

        // Once filtering and transforming are done collector function can be applied (if present)
        val collectedResults = vaultNamedQuery.collector?.collect(
            filteredAndTransformedResults,
            deserializedParams
        )?.results?.filterNotNull() ?: filteredAndTransformedResults

        // Return the filtered/transformed/collected (if present) result to the caller
        val response = EntityResponse.newBuilder()
            .setResults(collectedResults.map { ByteBuffer.wrap(serializationService.serialize(it).bytes) })

        response.resumePoint = resumePoint?.let { ByteBuffer.wrap(serializationService.serialize(it).bytes) }
        response.metadata = KeyValuePairList(emptyList())

        return response.build()
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
     * The returned [ProcessedQueryResults] object provides the collated query results
     * post-filtering, a [ResumePoint] if there is another page of data to be returned, and the
     * total number of rows returned from executed queries for informational purposes.
     */
    private fun filterResultsAndFillPageSize(
        request: FindWithNamedQuery,
        vaultNamedQuery: VaultNamedQuery,
        deserializedParams: Map<String, Any?>
    ): ProcessedQueryResults {
        val filteredRawData = mutableListOf<RawQueryData>()

        var currentRetry = 0
        var numberOfRowsFromQuery = 0
        var currentResumePoint = request.resumePoint?.let {
            serializationService.deserialize<ResumePoint>(request.resumePoint.array())
        }

        while (filteredRawData.size < request.limit && currentRetry < RESULT_SET_FILL_RETRY_LIMIT) {
            ++currentRetry

            log.trace { "Executing try: $currentRetry, fetched ${filteredRawData.size} number of results so far." }

            // Fetch the state and refs for the given transaction IDs
            val rawResults = try {
                fetchStateAndRefs(
                    request,
                    vaultNamedQuery.query.query,
                    currentResumePoint
                )
            } catch (e: Exception) {
                log.warn(
                    "Failed to query \"${request.queryName}\" " +
                        "with parameters \"${deserializedParams}\" limit \"${request.limit}\".",
                    e
                )
                throw e
            }

            // If we have no filter, there's no need to continue the loop
            if (vaultNamedQuery.filter == null) {
                with(rawResults) {
                    return ProcessedQueryResults(
                        results.map { it.stateAndRef },
                        if (hasMore) results.last().resumePoint else null,
                        results.size
                    )
                }
            }

            rawResults.results.forEach { result ->
                ++numberOfRowsFromQuery
                if (vaultNamedQuery.filter.filter(result.stateAndRef, deserializedParams)) {
                    filteredRawData.add(result)
                }

                if (filteredRawData.size >= request.limit) {
                    // Page filled. We need to set the resume point based on the final filtered
                    // result (as we may be throwing out additional records returned by the query).
                    // Note that we should never get to the > part of the condition; this is a
                    // purely defensive check.
                    //
                    // There are more results if either we didn't get through all the results
                    // returned by the query invocation, or if the query itself indicated there are
                    // more results to return.
                    val moreResults = (result != rawResults.results.last()) || rawResults.hasMore

                    return ProcessedQueryResults(
                        filteredRawData.map { it.stateAndRef },
                        if (moreResults) filteredRawData.last().resumePoint else null,
                        numberOfRowsFromQuery
                    )
                }
            }

            // If we can't fetch more states we just return the result set as-is
            if (!rawResults.hasMore) {
                currentResumePoint = null
                break
            } else {
                currentResumePoint = rawResults.results.last().resumePoint
            }
        }

        return ProcessedQueryResults(
            filteredRawData.map { it.stateAndRef },
            currentResumePoint,
            numberOfRowsFromQuery
        )
    }

    /**
     * A function that fetches the contract states that belong to the given transaction IDs.
     * The data stored in the component table will be deserialized into contract states using
     * component groups.
     *
     * Each invocation of this function represents a single distinct query to the database.
     */
    private fun fetchStateAndRefs(
        request: FindWithNamedQuery,
        whereJson: String?,
        resumePoint: ResumePoint?
    ): RawQueryResults {
        @Suppress("UNCHECKED_CAST")
        val resultList = entityManagerFactory.transaction { em ->

            val resumePointExpr = resumePoint?.let {
                " AND ((visible_states.created > :created) OR " +
                    "(visible_states.created = :created AND tc_output.transaction_id > :txId) OR " +
                    "(visible_states.created = :created AND tc_output.transaction_id = :txId AND tc_output.leaf_idx > :leafIdx))"
            } ?: ""

            val query = em.createNativeQuery(
                """
                    SELECT tc_output.transaction_id,
                        tc_output.leaf_idx,
                        tc_output_info.data as output_info_data,
                        tc_output.data AS output_data,
                        visible_states.created AS created
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
                        $resumePointExpr
                        AND visible_states.created <= :$TIMESTAMP_LIMIT_PARAM_NAME
                        ORDER BY visible_states.created, tc_output.transaction_id, tc_output.leaf_idx
                """,
                Tuple::class.java
            )

            if (resumePoint != null) {
                log.trace { "Query is resuming from $resumePoint" }
                query.setParameter("created", resumePoint.created)
                query.setParameter("txId", resumePoint.txId)
                query.setParameter("leafIdx", resumePoint.leafIdx)
            }

            request.parameters.forEach { rec ->
                query.setParameter(rec.key, rec.value?.let { serializationService.deserialize(it.array()) })
            }

            query.firstResult = request.offset
            // Getting one more than requested allows us to identify if there are more results to
            // return in a subsequent page
            query.maxResults = request.limit + 1

            query.resultList as List<Tuple>
        }

        return if (resultList.size > request.limit) {
            // We need to truncate the list to the number requested, but also flag that there is
            // another page to be returned
            RawQueryResults(resultList.subList(0, request.limit).map { RawQueryData(it) }, hasMore = true)
        } else {
            RawQueryResults(resultList.map { RawQueryData(it) }, hasMore = false)
        }
    }
}
