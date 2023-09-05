package net.corda.flow.persistence.query

import net.corda.v5.application.persistence.PagedQuery.ResultSet
import net.corda.v5.base.annotations.Suspendable
import java.io.Serializable
import java.nio.ByteBuffer

/**
 * [ResultSetExecutor] defines the database operation that is executed to retrieve data within [ResultSet.next].
 */
fun interface ResultSetExecutor<R> : Serializable {

    /**
     * Retrieve data for a [ResultSet].
     *
     * @param serializedParameters The serialized parameters of the [ResultSet].
     * @param offset The current offset of the [ResultSet].
     *
     * @return A [Results] containing the serialized results, the number of rows the database retrieved from its query.
     */
    @Suspendable
    fun execute(serializedParameters: Map<String, ByteBuffer>, offset: Int): Results

    data class Results(val serializedResults: List<ByteBuffer>, val numberOfRowsFromQuery: Int)
}