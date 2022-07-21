package net.corda.v5.application.persistence

/**
 * Used to build a Query that supports limit and offset.
 *
 * @param R the type of the results.
 */
interface PagedQuery<R> : Query<R> {
    /**
     * Set the maximum number of results to return.
     * If no limit is set, all records will be returned.
     *
     * @param limit maximum number of results to return.
     * @return the same [Query] instance.
     */
    fun setLimit(limit: Int): PagedQuery<R>

    /**
     * Set the index of the first result in the query to return.
     * A default of `0` will be used in case it is not set.
     *
     * @param offset the index of the first result in the query to return.
     * @return the same [Query] instance.
     */
    fun setOffset(offset: Int): PagedQuery<R>
}
