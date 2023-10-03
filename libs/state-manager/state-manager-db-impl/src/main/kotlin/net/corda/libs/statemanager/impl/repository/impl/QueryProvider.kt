package net.corda.libs.statemanager.impl.repository.impl

import net.corda.libs.statemanager.api.Operation

/**
 * Provider for SQL queries executed by [StateRepositoryImpl].
 * When using ANSI SQL, the query string should be added to [AbstractQueryProvider] so it is shared across all
 * implementations. If no ANSI SQL is required for a particular RDBMS provider, the query string should be added to
 * the relevant implementation instead.
 */
interface QueryProvider {

    val createState: String

    val updateState: String

    val findStatesByKey: String

    val deleteStatesByKey: String

    val findStatesUpdatedBetween: String

    // TODO-[CORE-17025]: make below methods regular queries with parameters instead of embedding the filter value.
    fun statesFilteredByMetadataKey(key: String, operation: Operation, value: Any): String

    fun statesUpdatedBetweenAndFilteredByMetadataKey(key: String, operation: Operation, value: Any): String
}
