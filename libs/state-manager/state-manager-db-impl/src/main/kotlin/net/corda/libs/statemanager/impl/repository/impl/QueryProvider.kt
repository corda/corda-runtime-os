package net.corda.libs.statemanager.impl.repository.impl

import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.impl.model.v1.StateEntity

/**
 * Provider for SQL queries executed by [StateRepositoryImpl].
 * When using ANSI SQL, the query string should be added to [AbstractQueryProvider] so it is shared across all
 * implementations. If no ANSI SQL is required for a particular RDBMS provider, the query string should be added to
 * the relevant implementation instead.
 */
interface QueryProvider {

    val createState: String

    val findStatesByKey: String

    val deleteStatesByKey: String

    val findStatesUpdatedBetween: String

    fun updateStates(states: Collection<StateEntity>): String

    // TODO-[CORE-17025]: make below methods regular queries with parameters instead of embedding the filter value.
    fun findStatesByMetadataMatchingAll(filters: Collection<MetadataFilter>): String

    fun findStatesByMetadataMatchingAny(filters: Collection<MetadataFilter>): String

    fun findStatesUpdatedBetweenAndFilteredByMetadataKey(filter: MetadataFilter): String
}
