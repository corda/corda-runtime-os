package net.corda.flow.application.persistence.query

import net.corda.flow.persistence.query.StableResultSetExecutor
import net.corda.v5.application.persistence.PagedQuery
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import java.lang.IllegalStateException
import java.nio.ByteBuffer

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

    private companion object {
        private const val RESUME_POINT_PARAM_NAME = "Corda_QueryResumePoint"
    }

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

        if (resumePoint != null) {
            serializedParameters[RESUME_POINT_PARAM_NAME] = resumePoint!!
        } else {
            serializedParameters.remove(RESUME_POINT_PARAM_NAME)
        }

        val serializedResults = resultSetExecutor.execute(serializedParameters).serializedResults

        if (serializedResults.size > limit + 1) {
            throw IllegalStateException("The query returned too many results")
        }

        results = if (serializedResults.size <= limit) {
            serializedResults
        } else {
            serializedResults.subList(0, limit)
        }
            .map { serializationService.deserialize(it.array(), resultClass) }

        val newResumePoint = serializedResults.getOrNull(limit)

        if (newResumePoint != null && newResumePoint == resumePoint) {
            throw IllegalStateException("Infinite query detected; resume point has not been updated")
        } else {
            resumePoint = newResumePoint
        }

        firstExecution = false

        return results
    }
}
