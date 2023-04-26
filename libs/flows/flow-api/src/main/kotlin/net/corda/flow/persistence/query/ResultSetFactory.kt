package net.corda.flow.persistence.query

import net.corda.v5.application.persistence.PagedQuery.ResultSet

interface ResultSetFactory {

    fun <R> create(
        parameters: Map<String, Any>,
        limit: Int,
        offset: Int,
        resultClass: Class<R>,
        resultSetExecutor: ResultSetExecutor<R>
    ): ResultSet<R>
}