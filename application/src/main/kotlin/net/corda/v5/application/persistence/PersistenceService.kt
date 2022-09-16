@file:JvmName("PersistenceUtils")
package net.corda.v5.application.persistence

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.annotations.Suspendable

/**
 * [PersistenceService] allows a flow to insert, find, update and delete custom entities in the persistent
 * store provided by the platform.
 *
 * Corda provides an instance of [PersistenceService] to flows via property injection.
 */
@DoNotImplement
@Suppress("LongParameterList", "TooManyFunctions")
interface PersistenceService {

    /**
     * Persists a single [entity] to the store.
     *
     * @param entity The entity to persist.
     *
     * @throws IllegalArgumentException If [entity] is a primitive type.
     * @throws CordaPersistenceException If an error occurs during execution.
     */
    @Suspendable
    fun persist(entity: Any)

    /**
     * Persists multiple [entities] in the persistence context in a single transaction.
     *
     * @param entities List of entities to be persisted.
     *
     * @throws IllegalArgumentException If [entities] contains any primitive types.
     * @throws CordaPersistenceException If an error occurs during execution.
     */
    @Suspendable
    fun persist(entities: List<Any>)

    /**
     * Merges a single [entity] in the persistence context in a transaction.
     *
     * @param entity The entity to merge.
     *
     * @return The merged entity.
     *
     * @throws IllegalArgumentException If [entity] is a primitive type.
     * @throws CordaPersistenceException If an error occurs during execution.
     */
    @Suspendable
    fun <T : Any> merge(entity: T): T?

    /**
     * Merges multiple [entities] in the persistence context in a single transaction.
     *
     * @param entities List of entities to be merged.
     *
     * @return The list of merged entities.
     *
     * @throws IllegalArgumentException If [entities] contains any primitive types.
     * @throws CordaPersistenceException If an error occurs during execution.
     */
    @Suspendable
    fun <T : Any> merge(entities: List<T>): List<T>

    /**
     * Removes a single [entity] from the persistence context in a transaction.
     *
     * @param entity The entity to remove.
     *
     * @throws IllegalArgumentException If [entity] is a primitive type.
     * @throws CordaPersistenceException If an error occurs during execution.
     */
    @Suspendable
    fun remove(entity: Any)

    /**
     * Removes multiple [entities] from the persistence context in a single transaction.
     *
     * @param entities List of entities to be removed.
     *
     * @throws IllegalArgumentException If [entities] contains any primitive types.
     * @throws CordaPersistenceException If an error occurs during execution.
     */
    @Suspendable
    fun remove(entities: List<Any>)

    /**
     * Finds a single entity in the persistence context of the specified entity type [T] and with the specified
     * [primaryKey].
     *
     * @param entityClass The type of entity to find.
     * @param primaryKey The primary key of the entity to find.
     *
     * @return The found entity. Null if it could not be found in the persistence context.
     *
     * @throws IllegalArgumentException If [entityClass] is a primitive type.
     * @throws CordaPersistenceException If an error occurs during execution.
     */
    @Suspendable
    fun <T : Any> find(entityClass: Class<T>, primaryKey: Any): T?

    /**
     * Finds multiple entities of the same type with different primary keys in a single transaction.
     *
     * @param entityClass The type of the entities to find.
     * @param primaryKeys List of primary keys to find with the given [entityClass] type.
     *
     * @return List of entities found. Empty list if none were found.
     *
     * @throws IllegalArgumentException If [entityClass] is a primitive type.
     * @throws CordaPersistenceException If an error occurs during execution.
     */
    @Suspendable
    fun <T : Any> find(entityClass: Class<T>, primaryKeys: List<Any>): List<T>

    /**
     * Creates a [PagedQuery] to find all entities of the same type from the persistence context in a single transaction.
     *
     * @param entityClass The type of the entities to find.
     * @return A [PagedQuery] That returns the list of entities found.
     *
     * @throws IllegalArgumentException If [entityClass] is a primitive type.
     * @throws CordaPersistenceException If an error occurs during execution.
     */
    @Suspendable
    fun <T : Any> findAll(entityClass: Class<T>): PagedQuery<T>

    /**
     * Creates a [ParameterisedQuery] to support a named query to return a list of entities of the given type in a
     * single transaction. Casts result set to the specified type [T].
     *
     * Example usage:
     *
     * - Kotlin:
     *
     * ```kotlin
     * // For JPA Entity:
     * @Suppress("Unused")
     * @CordaSerializable
     * @Entity
     * @Table(name = "DOGS")
     * @NamedQuery(name = "find_by_name_and_age", query = "SELECT d FROM Dog d WHERE d.name = :name AND d.age <= :maxAge")
     * class Dog {
     *     @Id
     *     private val id: UUID? = null
     *
     *     @Column(name = "DOG_NAME", length = 50, nullable = false, unique = false)
     *     private val name: String? = null
     *
     *     @Column(name = "DOG_AGE")
     *     private val age: Int? = null // getters and setters
     *     // ...
     * }
     *
     * // create a named query setting parameters one-by-one, that returns the second page of up to 100 records
     * val pagedQuery = persistenceService
     *     .query("find_by_name_and_age", Dog::class.java)
     *     .setParameter("name", "Felix")
     *     .setParameter("maxAge", 5)
     *     .setLimit(100)
     *     .setOffset(200)
     *
     * // execute the query and return the results as a List
     * val result1 = pagedQuery.execute()
     *
     * // create a named query setting parameters as Map, that returns the second page of up to 100 records
     * val paramQuery = persistenceService
     *     .query("find_by_name_and_age", Dog::class.java)
     *     .setParameters(mapOf(Pair("name", "Felix"), Pair("maxAge", 5)))
     *     .setLimit(100)
     *     .setOffset(200)
     *
     * // execute the query and return the results as a List
     * val result2 = pagedQuery.execute()
     * ```
     *
     * - Java:
     *
     * ```java
     *
     * // For JPA Entity:
     * @CordaSerializable
     * @Entity
     * @Table(name = "DOGS")
     * @NamedQuery(name = "find_by_name_and_age", query = "SELECT d FROM Dog d WHERE d.name = :name AND d.age <= :maxAge")
     * class Dog {
     *     @Id
     *     private UUID id;
     *     @Column(name = "DOG_NAME", length = 50, nullable = false, unique = false)
     *     private String name;
     *     @Column(name = "DOG_AGE")
     *     private Integer age;
     *
     *     // getters and setters
     *      ...
     * }
     *
     * // create a named query setting parameters one-by-one, that returns the second page of up to 100 records
     * ParameterisedQuery<Dog> pagedQuery = persistenceService
     *         .query("find_by_name_and_age", Dog.class)
     *         .setParameter("name", "Felix")
     *         .setParameter("maxAge", 5)
     *         .setLimit(100)
     *         .setOffset(200);
     *
     * // execute the query and return the results as a List
     * List<Dog> result1 = pagedQuery.execute();
     *
     * // create a named query setting parameters as Map, that returns the second page of up to 100 records
     * ParameterisedQuery<Dog> paramQuery = persistenceService
     *         .query("find_by_name_and_age", Dog.class)
     *         .setParameters(Map.of("name", "Felix", "maxAge", 5))
     *         .setLimit(100)
     *         .setOffset(200);
     *
     * // execute the query and return the results as a List
     * List<Dog> result2 = pagedQuery.execute();
     *```
     * @param queryName The name of the named query registered in the persistence context.
     * @param entityClass The type of the entities to find.
     * @param T The type of the results.
     *
     * @return A [ParameterisedQuery] that returns the list of entities found. Empty list if none were found.
     *
     * @throws IllegalArgumentException If [entityClass] is a primitive type.
     */
    @Suspendable
    fun <T : Any> query(
        queryName: String,
        entityClass: Class<T>
    ): ParameterisedQuery<T>
}

/**
 * Finds a single entity in the persistence context of the specified entity type [T] and with the specified
 * [primaryKey].
 *
 * @param primaryKey The primary key of the entity to find.
 * @param T The type of entity to find.
 *
 * @return The found entity. Null if it could not be found in the persistence context.
 */
inline fun <reified T : Any> PersistenceService.find(primaryKey: Any): T? = find(T::class.java, primaryKey)

/**
 * Finds multiple entities of the same type with different primary keys from the persistence context in a single
 * transaction.
 *
 * @param primaryKeys List of primary keys to find with the given entity type [T].
 * @param T The type of the entities to find.
 *
 * @return List of entities found. Empty list if none were found.
 */
inline fun <reified T : Any> PersistenceService.find(primaryKeys: List<Any>): List<T> = find(T::class.java, primaryKeys)

/**
 * Finds all entities of the same type in a single transaction.
 *
 * @param T The type of the entities to find.
 *
 * @return A [PagedQuery] That returns the list of entities found. Empty list if none were found.
 */
inline fun <reified T : Any> PersistenceService.findAll(): PagedQuery<T> = findAll(T::class.java)
