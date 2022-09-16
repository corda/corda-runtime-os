package net.corda.v5.application.persistence

/**
 * Used to build a Query that supports parameters.
 *
 * @param R the type of the results.
 */
interface ParameterisedQuery<R> : PagedQuery<R> {

    /**
     * Sets the maximum number of results to return.
     *
     * If no limit is set, all records will be returned.
     *
     * @param limit The maximum number of results to return.
     *
     * @return The same [ParameterisedQuery] instance.
     *
     * @throws IllegalArgumentException If [limit] is negative.
     *
     * @see PagedQuery.setLimit
     */
    override fun setLimit(limit: Int): ParameterisedQuery<R>

    /**
     * Sets the index of the first result in the query to return.
     *
     * A default of `0` will be used if it is not set.
     *
     * @param offset The index of the first result in the query to return.
     *
     * @return The same [ParameterisedQuery] instance.
     *
     * @throws IllegalArgumentException If [offset] is negative.
     *
     * @see PagedQuery.setOffset
     */
    override fun setOffset(offset: Int): ParameterisedQuery<R>

    /**
     * Sets the parameter with given [name].
     *
     * @param name The name of the parameter in the [ParameterisedQuery].
     * @param value The value of the parameter to use in the [ParameterisedQuery].
     *
     * @return the same [ParameterisedQuery] instance.
     */
    fun setParameter(name: String, value: Any): ParameterisedQuery<R>

    /**
     * Sets the parameters as a [Map].
     *
     * @param parameters To parameters be used in the [ParameterisedQuery]
     *
     * @return The same [ParameterisedQuery] instance.
     */
    fun setParameters(parameters: Map<String, Any>): ParameterisedQuery<R>
}
