package net.corda.simulator.runtime.persistence

import net.corda.v5.application.persistence.PagedQuery
import java.io.Serializable

/**
 * Simulator implementation of `net.corda.flow.application.persistence.query.impl.ResultSetImpl`.
 */
data class SimResultSetImpl<R> internal constructor(
    private var limit: Int,
    private var offset: Int,
    private val resultSetExecutor: ResultSetExecutor<R>
) : PagedQuery.ResultSet<R> {

    private var results: List<R> = emptyList()
    private var numberOfRowsFromQuery: Int = 0
    private var hasNext: Boolean = true

    override fun getResults(): List<R> {
        return results
    }

    override fun hasNext(): Boolean {
        return hasNext
    }

    override fun next(): List<R> {
        if (!hasNext()) {
            throw NoSuchElementException("The result set has no more pages to query")
        }
        val (results, numberOfRowsFromQuery) = resultSetExecutor.execute(offset)
        this.numberOfRowsFromQuery = numberOfRowsFromQuery
        this.hasNext = numberOfRowsFromQuery == limit
        this.offset += limit
        this.results = results
        return this.results
    }
}

fun interface ResultSetExecutor<R> : Serializable {

    fun execute(offset: Int): Results<R>

    data class Results<R>(val results: List<R>, val numberOfRowsFromQuery: Int)
}
