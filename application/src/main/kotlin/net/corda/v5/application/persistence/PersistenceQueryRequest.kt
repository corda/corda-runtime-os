package net.corda.v5.application.persistence

import net.corda.v5.application.persistence.query.NamedQueryFilter

/**
 * Request object for making Query requests on the Persistence Service.
 *
 * @param queryName the name of the named query registered in the persistence context.
 * @param namedParameters the named parameters to be set in the named query.
 * @param postFilter the filter to be applied after named query execution.
 * @param postProcessorName the name of the post-processor that will process named query results.
 */
data class PersistenceQueryRequest(
    /**
     * The name of the named query registered in the persistence context.
     */
    val queryName: String,
    /**
     * The named parameters to be set in the named query.
     */
    val namedParameters: Map<String, Any>,
    /**
     * The filter to be applied after named query execution.
     */
    val postFilter: NamedQueryFilter?,
    /**
     * The name of the post-processor that will process named query results.
     */
    val postProcessorName: String?
) {
    /**
     * Request object for making query requests on the Persistence Service
     *
     * @param queryName the name of the named query registered in the persistence context.
     * @param namedParameters the named parameters to be set in the named query.
     */
    constructor(queryName: String, namedParameters: Map<String, Any>)
            : this(queryName, namedParameters, null, null)

    /**
     * Request object for making query requests on the Persistence Service
     *
     * @param queryName the name of the named query registered in the persistence context.
     * @param namedParameters the named parameters to be set in the named query.
     * @param postFilter the filter to be applied after named query execution.
     */
    constructor(
        queryName: String,
        namedParameters: Map<String, Any>,
        postFilter: NamedQueryFilter?
    )
            : this(queryName, namedParameters, postFilter, null)

    class Builder(private val queryName: String, private val namedParameters: Map<String, Any>) {
        private var postFilter: NamedQueryFilter? = null
        private var postProcessorName: String? = null

        fun build() = PersistenceQueryRequest(queryName, namedParameters, postFilter, postProcessorName)

        fun withPostFilter(postFilter: NamedQueryFilter): Builder = apply { this.postFilter = postFilter }

        fun withPostProcessor(postProcessorName: String): Builder = apply { this.postProcessorName = postProcessorName }
    }
}
