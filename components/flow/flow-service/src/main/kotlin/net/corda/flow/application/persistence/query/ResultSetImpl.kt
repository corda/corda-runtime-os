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
        val (serializedResults, numberOfRowsFromQuery) = resultSetExecutor.execute(serializedParameters, offset)

        // Here have the number of rows we fetched from the database (before filtering)
        // then we check if the remainder is 0 when diving the number of rows fetched with the limit
        // This will:
        // 1. yield false only if we don't have a next page to fetch
        // Examples:
        // `limit` = 5, `numberOfRowsFromQuery` = 9, `hasNext = 9 % 5 == 0` => `hasNext = 4 == 0` == false
        // `limit` = 5, `numberOfRowsFromQuery` = 4, `hasNext = 4 % 5 == 0` => `hasNext = 4 == 0` == false
        // 2. yield true if we can't know for sure if there's a next page
        // Example:
        // `limit` = 5, `numberOfRowsFromQuery` = 5, `hasNext = 5 % 5 == 0` => `hasNext =  0 == 0` == true
        hasNext = numberOfRowsFromQuery != 0  // If there are no rows left there's no more pages to fetch
                && limit != 0 // If the limit is 0 it means we fetch everything in one go so there are no pages left
                && numberOfRowsFromQuery % limit == 0
        offset += numberOfRowsFromQuery
        results = serializedResults.map { serializationService.deserialize(it.array(), resultClass) }
        return results
    }
}
