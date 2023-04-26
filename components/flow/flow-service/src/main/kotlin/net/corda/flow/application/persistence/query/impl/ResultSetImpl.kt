package net.corda.flow.application.persistence.query.impl

import net.corda.flow.persistence.query.ResultSetExecutor
import net.corda.v5.application.persistence.PagedQuery
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import java.nio.ByteBuffer

// in execute the query the first time within the result set, calling `getResults` will return the results of the already executed
// query. This will simplify the code slightly. Means that ALL query execution happens within the `ResultSet`, even if `Query.execute`
// just delegates to it, all that matters is that the first execution of the query happens somewhere in the `Query.execute` stack frame.

// have a query executor?
// that actually executes the query, deserializes the results, this would then decrease the duplication in the query impl

// consider splitting this from the vault one because the persistence one is simpler to execute, as there is no in-memory filtering
data class ResultSetImpl<R> internal constructor(
    private val serializationService: SerializationService,
    private var serializedParameters: Map<String, ByteBuffer>,
    private var limit: Int,
    private var offset: Int,
    private val resultClass: Class<R>,
    private val resultSetExecutor: ResultSetExecutor<R>
) : PagedQuery.ResultSet<R> {

    private var results: List<R> = emptyList()
    private var numberOfRowsFromQuery: Int = 0

    override fun getResults(): List<R> {
        return results
    }

    override fun hasNext(): Boolean {
        return numberOfRowsFromQuery == limit
    }

    @Suspendable
    override fun next(): List<R> {
        val (serializedResults, numberOfRowsFromQuery) = resultSetExecutor.execute(serializedParameters, offset)
        this.numberOfRowsFromQuery = numberOfRowsFromQuery
        this.offset += limit
        this.results = serializedResults.map { serializationService.deserialize(it.array(), resultClass) }
        return this.results
    }
}
