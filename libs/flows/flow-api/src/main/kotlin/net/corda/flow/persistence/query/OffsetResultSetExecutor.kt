package net.corda.flow.persistence.query

import net.corda.v5.application.persistence.PagedQuery.ResultSet
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.Suspendable
import java.io.Serializable
import java.nio.ByteBuffer

/**
 * [OffsetResultSetExecutor] defines the database operation that is executed to retrieve data within [ResultSet.next].
 *
 * Offset based queries are not stable, and can miss data if using paging and the where clause / ordering criteria
 * are mutable. It is strongly recommended to use [StableResultSetExecutor] instead, which should always return
 * data reliably, and is likely to be more performant.
 */
fun interface OffsetResultSetExecutor<R> : Serializable {

    /**
     * Retrieve data for a [ResultSet].
     *
     * @param serializedParameters The serialized parameters of the [ResultSet].
     * @param offset The current offset of the [ResultSet].
     *
     * @return A [Results] containing the serialized results, the number of rows the database retrieved from its query.
     */
    @Suspendable
    fun execute(serializedParameters: Map<String, ByteBuffer?>, offset: Int): Results

    @CordaSerializable
    data class Results(val serializedResults: List<ByteArray>, val numberOfRowsFromQuery: Int)
}
