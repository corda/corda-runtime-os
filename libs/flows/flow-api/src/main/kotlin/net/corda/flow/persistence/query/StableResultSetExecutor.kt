package net.corda.flow.persistence.query

import net.corda.v5.application.persistence.PagedQuery.ResultSet
import net.corda.v5.base.annotations.Suspendable
import java.io.Serializable
import java.nio.ByteBuffer

/**
 * [StableResultSetExecutor] defines the database operation that is executed to retrieve data within [ResultSet.next].
 *
 * Stable query executors should generally be used in favor of [OffsetResultSetExecutor], as they are guaranteed
 * to return all results, and are likely to be more performant in cases where many pages of data are to be
 * returned. However, to work reliably, the query writer must ensure that:
 *
 * * The ORDER_BY expression of the query must contain only immutable fields. These fields must collectively form a
 *   unique key for the database row, and must be monotonically increasing.
 * * The WHERE expression of the query must include the fields in the ORDER_BY expression in the form:
 *   `WHERE ... AND ((A > <Prev A value>) OR (A = <Prev A value> AND B > <Prev B value>))`. In this case, fields A
 *   and B represent the first and second fields from an ORDER_BY expression that contains two fields, and the
 *   "previous" values are populated from the `resumePoint` data passed in on query execution. This expression
 *   should be adapted based on the number of fields in the ORDER_BY expression.
 *
 * See [VaultNamedQueryExecutorImpl][net.corda.ledger.persistence.query.execution.impl.VaultNamedQueryExecutorImpl]
 * for example usage.
 */
fun interface StableResultSetExecutor<R> : Serializable {

    /**
     * Retrieve data for a [ResultSet].
     *
     * @param serializedParameters The serialized parameters of the [ResultSet].
     * @param resumePoint Opaque data that communicates the resumption point to a query when this is executing in
     *                    the context of getting a subsequent page of data.
     * @param offset Optional offset for the results.
     *
     * @return A [Results] containing the serialized results and the resumption point to use when executing in the
     *         context of getting a subsequent page of data.
     */
    @Suspendable
    fun execute(serializedParameters: Map<String, ByteBuffer?>, resumePoint: ByteBuffer?, offset: Int?): Results

    data class Results(val serializedResults: List<ByteBuffer>, val resumePoint: ByteBuffer?, val numberOfRowsFromQuery: Int?)
}
