package net.corda.flow.application.persistence.query

import net.corda.flow.persistence.query.OffsetResultSetExecutor
import net.corda.v5.application.persistence.PagedQuery
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import java.nio.ByteBuffer

/**
 * Captures results and paging data from a query that supports offset based pagination.
 */
data class OffsetResultSetImpl<R> internal constructor(
    private val serializationService: SerializationService,
    private var serializedParameters: Map<String, ByteBuffer?>,
    private var limit: Int,
    private var offset: Int,
    private val resultClass: Class<R>,
    private val resultSetExecutor: OffsetResultSetExecutor<R>
) : PagedQuery.ResultSet<R> {

    init {
        // Ideally this will never happen, but we keep this check in here for safety
        require(offset >= 0) {
            "Offset cannot be negative"
        }
        require(limit > 0) {
            "Limit cannot be negative or zero"
        }
    }

    private var results: List<R> = emptyList()
    private var hasNext: Boolean = true

    override fun getResults(): List<R> {
        return results
    }

    override fun hasNext(): Boolean {
        return hasNext
    }

    @Suspendable
    override fun next(): List<R> {
        if (!hasNext()) {
            throw NoSuchElementException("The result set has no more pages to query")
        }
        val (serializedResults, numberOfRowsFromQuery) = resultSetExecutor.execute(serializedParameters, offset)

        hasNext = numberOfRowsFromQuery != 0  // If there are no rows left there are no more pages to fetch
                && serializedResults.size == limit // If the current page is full, it means we might have more records, so we go and check
        offset += numberOfRowsFromQuery
        results = serializedResults.map { serializationService.deserialize(it, resultClass) }
        return results
    }
}
