package net.corda.v5.application.persistence

import net.corda.v5.base.annotations.Suspendable

/**
 * Used to build a Query that supports limit and offset.
 *
 * @param R The type of the results.
 */
interface PagedQuery<R> {

    /**
     * Sets the maximum number of results to return.
     *
     * If no limit is set, all records will be returned.
     *
     * @param limit The maximum number of results to return.
     *
     * @return The same [PagedQuery] instance.
     *
     * @throws IllegalArgumentException If [limit] is negative.
     */
    fun setLimit(limit: Int): PagedQuery<R>

    /**
     * Sets the index of the first result in the query to return.
     *
     * A default of `0` will be used if it is not set.
     *
     * @param offset The index of the first result in the query to return.
     *
     * @return The same [PagedQuery] instance.
     *
     * @throws IllegalArgumentException If [offset] is negative.
     */
    fun setOffset(offset: Int): PagedQuery<R>

    /**
     * Executes the [PagedQuery]
     *
     * @return List of entities found. Empty list if none were found.
     *
     * @throws CordaPersistenceException If there is an error executing the query.
     */
    @Suspendable
    fun execute(): List<R>
}
