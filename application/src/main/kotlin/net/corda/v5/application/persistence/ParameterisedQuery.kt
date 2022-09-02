package net.corda.v5.application.persistence

/**
 * Used to build a Query that supports parameters.
 *
 * @param R the type of the results.
 */
interface ParameterisedQuery<R> : PagedQuery<R> {

    /**
     * @see PagedQuery.setLimit
     */
    override fun setLimit(limit: Int): ParameterisedQuery<R>

    /**
     * @see PagedQuery.setOffset
     */
    override fun setOffset(offset: Int): ParameterisedQuery<R>

    /**
     * Set parameter with given [name].
     *
     * @param name of the parameter in the [Query].
     * @param value of the parameter to use in the [Query].
     * @return the same [ParameterisedQuery] instance.
     */
    fun setParameter(name: String, value: Any): ParameterisedQuery<R>

    /**
     * Set parameters as a [Map].
     *
     * @param parameters to be used in the [Query]
     * @return the same [ParameterisedQuery] instance.
     */
    fun setParameters(parameters: Map<String, Any>): ParameterisedQuery<R>
}
