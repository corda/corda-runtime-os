package net.corda.flow.application.persistence.query

import net.corda.flow.persistence.query.ResultSetExecutor
import net.corda.v5.application.persistence.PagedQuery
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import java.nio.ByteBuffer

data class ResultSetImpl<R> internal constructor(
    private val serializationService: SerializationService,
    private var serializedParameters: Map<String, ByteBuffer>,
    private var limit: Int,
    private var offset: Int,
    private val resultClass: Class<R>,
    private val resultSetExecutor: ResultSetExecutor<R>
) : PagedQuery.ResultSet<R> {

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
        val (serializedResults, numberOfRowsFromQuery, newOffset) = resultSetExecutor.execute(serializedParameters, offset)
        hasNext = limit in 1..numberOfRowsFromQuery
        offset = newOffset
        results = serializedResults.map { serializationService.deserialize(it.array(), resultClass) }
        return results
    }
}
