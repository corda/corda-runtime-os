package net.corda.flow.application.persistence.query

import net.corda.flow.persistence.query.StableResultSetExecutor
import net.corda.v5.application.persistence.PagedQuery
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import java.lang.IllegalStateException
import java.nio.ByteBuffer

/**
 * Captures results and paging data from a query that supports stable pagination.
 */
data class StableResultSetImpl<R> internal constructor(
    private val serializationService: SerializationService,
    private var serializedParameters: MutableMap<String, ByteBuffer>,
    private var limit: Int,
    private val resultClass: Class<R>,
    private val resultSetExecutor: StableResultSetExecutor<R>
) : PagedQuery.ResultSet<R> {

    private var results: List<R> = emptyList()
    private var resumePoint: ByteBuffer? = null
    private var firstExecution = true

    override fun getResults(): List<R> {
        return results
    }

    override fun hasNext(): Boolean {
        return resumePoint != null
    }

    @Suspendable
    override fun next(): List<R> {
        if (!firstExecution && !hasNext()) {
            throw NoSuchElementException("The result set has no more pages to query")
        }

        val (serializedResults, nextResumePoint) = resultSetExecutor.execute(
            serializedParameters,
            if (firstExecution) null else resumePoint
        )

        if (serializedResults.size > limit) {
            throw IllegalStateException("The query returned too many results")
        }

        results = serializedResults.map { serializationService.deserialize(it.array(), resultClass) }

        if (nextResumePoint != null && nextResumePoint == resumePoint) {
            throw IllegalStateException("Infinite query detected; resume point has not been updated")
        } else {
            resumePoint = nextResumePoint
        }

        firstExecution = false

        return results
    }
}
