package net.corda.v5.application.persistence

import net.corda.v5.application.injection.CordaFlowInjectable
import net.corda.v5.application.injection.CordaServiceInjectable
import net.corda.v5.application.persistence.query.NamedQueryFilter
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.stream.Cursor

/**
 * Persistence Service API providing functionality to interact with an entityManager and execute pre-defined named queries.
 */
@DoNotImplement
@Suppress("LongParameterList", "TooManyFunctions")
interface PersistenceService : CordaFlowInjectable, CordaServiceInjectable {
    /**
     * Persist a single [entity] in the persistence context in a transaction.
     *
     * @param entity the entity to persist.
     * @throws CordaPersistenceException if an error happens during persist operation
     */
    @Suspendable
    fun persist(entity: Any)

    /**
     * Persist multiple [entities] in the persistence context in a single transaction.
     *
     * @param entities list of entities to be persist.
     * @throws CordaPersistenceException if an error happens during persist operation
     */
    @Suspendable
    fun persist(entities: List<Any>)

    /**
     * Merge a single [entity] in the persistence context in a transaction.
     *
     * @param entity the entity to merge.
     * @return the merged entity.
     * @throws CordaPersistenceException if an error happens during merge operation
     */
    @Suspendable
    fun <T : Any> merge(entity: T): T?

    /**
     * Merge multiple [entities] in the persistence context in a single transaction.
     *
     * @param entities list of entities to be merged.
     * @return the list of merged entities.
     * @throws CordaPersistenceException if an error happens during merge operation
     */
    @Suspendable
    fun <T : Any> merge(entities: List<T>): List<T>

    /**
     * Remove a single [entity] from the persistence context in a transaction.
     *
     * @param entity the entity to remove.
     * @throws CordaPersistenceException if an error happens during remove operation
     */
    @Suspendable
    fun remove(entity: Any)

    /**
     * Remove multiple [entities] from the persistence context in a single transaction.
     *
     * @param entities list of entities to be remove.
     * @throws CordaPersistenceException if an error happens during remove operation
     */
    @Suspendable
    fun remove(entities: List<Any>)

    /**
     * Find a single entity in the persistence context given the [entityClass] and [primaryKey] of the entity.
     *
     * @param entityClass the type of entity to find.
     * @param primaryKey the primary key of the entity to find.
     * @return the found entity, null if it could not be found in the persistence context.
     * @throws CordaPersistenceException if an error happens during find operation
     */
    @Suspendable
    fun <T : Any> find(entityClass: Class<T>, primaryKey: Any): T?

    /**
     * Find multiple entities of the same type with different primary keys from the persistence context in a single transaction.
     *
     * @param entityClass the type of the entities to find.
     * @param primaryKeys list of primary keys to find with the given [entityClass] type.
     * @return list of entities found. Empty list if none were found.
     * @throws CordaPersistenceException if an error happens during find operation
     */
    @Suspendable
    fun <T : Any> find(entityClass: Class<T>, primaryKeys: List<Any>): List<T>

    /**
     * Execute a named query in a single transaction. Casts results to the specified type [R].
     *
     * @param queryName the name of the named query registered in the persistence context.
     * @param namedParameters the named parameters to be set in the named query.
     * @param R the type of the results
     * @return Cursor configured to poll data for this named query.
     * @throws CordaPersistenceException if an error happens during query operation
     */
    fun <R> query(
        queryName: String,
        namedParameters: Map<String, Any>
    ): Cursor<R>

    /**
     * Execute a named query in a single transaction and apply post filtering. Casts results to the specified type [R].
     *
     * @param queryName the name of the named query registered in the persistence context.
     * @param namedParameters the named parameters to be set in the named query.
     * @param postFilter the filter to be applied after named query execution.
     * @param R the type of the results
     * @return Cursor configured to poll data for this named query.
     * @throws CordaPersistenceException if an error happens during query operation
     */
    fun <R> query(
        queryName: String,
        namedParameters: Map<String, Any>,
        postFilter: NamedQueryFilter
    ): Cursor<R>

    /**
     * Execute a named query in a single transaction and apply post-processing to the results. Casts results to the specified type [R].
     *
     * @param queryName the name of the named query registered in the persistence context.
     * @param namedParameters the named parameters to be set in the named query.
     * @param postProcessorName the name of the post-processor that will process named query results.
     * @param R the type of the results
     * @return Cursor configured to poll data for this named query.
     * @throws CordaPersistenceException if an error happens during query operation
     */
    fun <R> query(
        queryName: String,
        namedParameters: Map<String, Any>,
        postProcessorName: String
    ): Cursor<R>

    /**
     * Execute a named query in a single transaction and apply post-filtering and post-processing to the results. Casts results to the specified type [R].
     *
     * @param queryName the name of the named query registered in the persistence context.
     * @param namedParameters the named parameters to be set in the named query.
     * @param postFilter the filter to be applied after named query execution.
     * @param postProcessorName the name of the post-processor that will process named query results.
     * @param R the type of the results
     * @return Cursor configured to poll data for this named query.
     * @throws CordaPersistenceException if an error happens during query operation
     */
    fun <R> query(
        queryName: String,
        namedParameters: Map<String, Any>,
        postFilter: NamedQueryFilter,
        postProcessorName: String
    ): Cursor<R>

    /**
     * Execute a named query in a single transaction with optional post-filtering and post-processing applied to the results. Casts results to the specified type [R].
     *
     * @param persistenceQueryRequest the request containing information to execute named queries with optional filtering and post-processing
     * @param R the type of the results
     * @return Cursor configured to poll data for this named query.
     * @throws CordaPersistenceException if an error happens during query operation
     */
    fun <R> query(persistenceQueryRequest: PersistenceQueryRequest): Cursor<R>
}

/**
 * Find a single entity in the persistence context given the entity type [T] and [primaryKey] of the entity.
 *
 * @param primaryKey the primary key of the entity to find.
 * @param T the type of entity to find.
 * @return the found entity, null if it could not be found in the persistence context.
 */
inline fun <reified T : Any> PersistenceService.find(primaryKey: Any): T? {
    return this.find(T::class.java, primaryKey)
}

/**
 * Find multiple entities of the same type with different primary keys from the persistence context in a single transaction.
 *
 * @param primaryKeys list of primary keys to find with the given entity type [T].
 * @param T the type of the entities to find.
 * @return list of entities found. Empty list if none were found.
 */
inline fun <reified T : Any> PersistenceService.find(primaryKeys: List<Any>): List<T> {
    return this.find(T::class.java, primaryKeys)
}