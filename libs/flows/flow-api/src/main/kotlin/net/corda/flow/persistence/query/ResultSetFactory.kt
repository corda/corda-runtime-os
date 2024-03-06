package net.corda.flow.persistence.query

import net.corda.v5.application.persistence.PagedQuery.ResultSet

/**
 * [ResultSetFactory] creates instances of [ResultSet].
 */
interface ResultSetFactory {

    /**
     * Create a [ResultSet] that queries and persistence operations use to retrieve data from the database.
     *
     * [ResultSet.next] is used to retrieve pages of data from the database. The arguments of [create] are used by [ResultSet.next].
     *
     * @param parameters The parameters of the query.
     * @param limit The limit of the query.
     * @param offset The offset of the query.
     * @param resultClass The return type of the query.
     * @param resultSetExecutor The operation that is executed to retrieve query results.
     *
     * @return A [ResultSet] that retrieves data based on the implementation of [offsetResultSetExecutor].
     */
    fun <R> create(
        parameters: Map<String, Any?>,
        limit: Int,
        offset: Int,
        resultClass: Class<R>,
        resultSetExecutor: OffsetResultSetExecutor<R>
    ): ResultSet<R>

    /**
     * Create a [ResultSet] that queries and persistence operations use to retrieve data from the database.
     *
     * [ResultSet.next] is used to retrieve pages of data from the database. The arguments of [create] are used by [ResultSet.next].
     *
     * @param parameters The parameters of the query.
     * @param limit The limit of the query.
     * @param offset The offset of the query
     * @param resultClass The return type of the query.
     * @param resultSetExecutor The operation that is executed to retrieve query results.
     *
     * @return A [ResultSet] that retrieves data based on the implementation of [offsetResultSetExecutor].
     */
    fun <R> createStable(
        parameters: Map<String, Any?>,
        limit: Int,
        offset: Int,
        resultClass: Class<R>,
        resultSetExecutor: StableResultSetExecutor<R>,
    ): ResultSet<R>
}
