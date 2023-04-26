package net.corda.flow.application.persistence.query

import net.corda.flow.application.persistence.external.events.FindAllExternalEventFactory
import net.corda.flow.application.persistence.external.events.FindAllParameters
import net.corda.flow.application.persistence.external.events.NamedQueryExternalEventFactory
import net.corda.flow.application.persistence.external.events.NamedQueryParameters
import net.corda.flow.application.persistence.wrapWithPersistenceException
import net.corda.flow.external.events.executor.ExternalEventExecutor
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
data class FindAllResultSetImpl<R>(
    private val externalEventExecutor: ExternalEventExecutor,
    private val serializationService: SerializationService,
    private var limit: Int,
    private var offset: Int,
    private val resultClass: Class<R>,


    //    private var results: List<R>,
    // using the original query means that the cordapp code could screw with the result sets behaviour
//    private val query: PagedQuery<R>,
//    private var numberOfRowsFromQuery: Int
) : PagedQuery.ResultSet<R> {

    private var results: List<R> = emptyList()
    private var numberOfRowsFromQuery = 0

    override fun getResults(): List<R> {
        return results
    }

    override fun hasNext(): Boolean {
        return numberOfRowsFromQuery == limit
    }

    @Suspendable
    override fun next(): List<R> {
        val (serializedResults, numberOfRowsFromQuery) = wrapWithPersistenceException {
            externalEventExecutor.execute(
                FindAllExternalEventFactory::class.java,
                FindAllParameters(resultClass, offset, limit)
            )
        }
        this.numberOfRowsFromQuery = numberOfRowsFromQuery
        this.offset += limit
        this.results = serializedResults.map { serializationService.deserialize(it.array(), resultClass) }
        return this.results
    }
}
