package net.corda.flow.application.persistence.query

import net.corda.flow.persistence.query.StableResultSetExecutor
import net.corda.v5.application.persistence.PagedQuery
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import java.nio.ByteBuffer

/**
 * Captures results and paging data from a query that supports stable pagination.
 */
data class StableResultSetImpl<R> internal constructor(
    private val serializationService: SerializationService,
    private var serializedParameters: MutableMap<String, ByteBuffer?>,
    private var limit: Int,
    private val resultClass: Class<R>,
    private val resultSetExecutor: StableResultSetExecutor<R>
) : PagedQuery.ResultSet<R> {

    private var results: List<R> = emptyList()
    private var resumePoint: ByteBuffer? = null
    private var firstExecution = true
    private var offset: Int = 0

    override fun getResults(): List<R> {
        return results
    }

    override fun hasNext(): Boolean {
        // A null resume point means that the query does not have any more data to return
        return resumePoint != null
    }

    @Suspendable
    override fun next(): List<R> {
        if (!firstExecution && !hasNext()) {
            throw NoSuchElementException("The result set has no more pages to query")
        }

        val (serializedResults, nextResumePoint, numberOfRowsFromQuery) =
            resultSetExecutor.execute(serializedParameters, resumePoint, offset)

        // We've got some serialized results.
        // Did we get too many?
        check(serializedResults.size <= limit) { "The query returned too many results" }

        // Unpack the serialized results
        results = serializedResults.map {
            serializationService.deserialize(it.array(), resultClass)
        }.also {
            check(nextResumePoint == null || nextResumePoint != resumePoint) {
                "Infinite query detected; resume point has not been updated"
            }

            // Do the bookkeeping, updating our state. We track both the offset
            // and the resume point, since we don't know
            // which the persistence worker is using.
            offset += numberOfRowsFromQuery ?: 0
            resumePoint = nextResumePoint
            firstExecution = false

        }
        return results
    }
}
