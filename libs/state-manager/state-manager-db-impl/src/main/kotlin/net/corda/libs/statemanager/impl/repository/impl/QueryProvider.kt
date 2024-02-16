package net.corda.libs.statemanager.impl.repository.impl

import net.corda.libs.statemanager.api.MetadataFilter

/**
 * Provider for SQL queries executed by [StateRepositoryImpl].
 * When using ANSI SQL, the query string should be added to [AbstractQueryProvider] so it is shared across all
 * implementations. If no ANSI SQL is required for a particular RDBMS provider, the query string should be added to
 * the relevant implementation instead.
 */
interface QueryProvider {

    fun createStates(size: Int): String

    val deleteStatesByKey: String
    val deleteStatesByKeyNoVersion: String

    val findStatesUpdatedBetween: String

    fun updateStates(size: Int): String

    fun findStatesByKey(size: Int): String

    fun findStatesByMetadataMatchingAll(filters: Collection<MetadataFilter>): String

    fun findStatesByMetadataMatchingAny(filters: Collection<MetadataFilter>): String

    fun findStatesUpdatedBetweenWithMetadataMatchingAll(filters: Collection<MetadataFilter>): String

    fun findStatesUpdatedBetweenWithMetadataMatchingAny(filters: Collection<MetadataFilter>): String
}
