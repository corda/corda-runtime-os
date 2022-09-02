package net.corda.v5.application.persistence

import net.corda.v5.base.annotations.Suspendable

/**
 * Used to build a Query.
 *
 * @param R the type of the results.
 */
interface Query<R> {
    /**
     * Execute the [Query]
     *
     * @return list of entities found. Empty list if none were found.
     *
     * @throws CordaPersistenceException If there is an error executing the query.
     */
    @Suspendable
    fun execute(): List<R>
}
