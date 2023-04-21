package net.corda.v5.application.persistence;

import net.corda.v5.base.annotations.DoNotImplement;
import net.corda.v5.base.annotations.Suspendable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * {@link PersistenceService} allows a flow to insert, find, update and delete custom entities in the persistent
 * store provided by the platform.
 * <p>
 * Corda provides an instance of {@link PersistenceService} to flows via property injection.
 */
@DoNotImplement
public interface PersistenceService {

    /**
     * Persists a single {@code entity} to the store.
     *
     * @param entity The entity to persist.
     *
     * @throws IllegalArgumentException If {@code entity} is a primitive type.
     * @throws CordaPersistenceException If an error occurs during execution.
     */
    @Suspendable
    void persist(@NotNull Object entity);

    /**
     * Persists multiple {@code entities} in the persistence context in a single transaction.
     *
     * @param entities List of entities to be persisted.
     *
     * @throws IllegalArgumentException If {@code entities} contains any primitive types.
     * @throws CordaPersistenceException If an error occurs during execution.
     */
    @Suspendable
    void persist(@NotNull List<?> entities);

    /**
     * Merges a single {@code entity} in the persistence context in a transaction.
     *
     * @param entity The entity to merge.
     *
     * @return The merged entity.
     *
     * @throws IllegalArgumentException If {@code entity} is a primitive type.
     * @throws CordaPersistenceException If an error occurs during execution.
     */
    @Suspendable
    @Nullable
    <T> T merge(@NotNull T entity);

    /**
     * Merges multiple {@code entities} in the persistence context in a single transaction.
     *
     * @param entities List of entities to be merged.
     *
     * @return The list of merged entities.
     *
     * @throws IllegalArgumentException If {@code entities} contains any primitive types.
     * @throws CordaPersistenceException If an error occurs during execution.
     */
    @Suspendable
    @NotNull
    <T> List<T> merge(@NotNull List<T> entities);

    /**
     * Removes a single {@code entity} from the persistence context in a transaction.
     *
     * @param entity The entity to remove.
     *
     * @throws IllegalArgumentException If {@code entity} is a primitive type.
     * @throws CordaPersistenceException If an error occurs during execution.
     */
    @Suspendable
    void remove(@NotNull Object entity);

    /**
     * Removes multiple {@code entities} from the persistence context in a single transaction.
     *
     * @param entities List of entities to be removed.
     *
     * @throws IllegalArgumentException If {@code entities} contains any primitive types.
     * @throws CordaPersistenceException If an error occurs during execution.
     */
    @Suspendable
    void remove(@NotNull List<?> entities);

    /**
     * Finds a single entity in the persistence context of the specified entity type [T] and with the specified
     * {@code primaryKey}.
     *
     * @param entityClass The type of entity to find.
     * @param primaryKey The primary key of the entity to find.
     *
     * @return The found entity. Null if it could not be found in the persistence context.
     *
     * @throws IllegalArgumentException If {@code entityClass} is a primitive type.
     * @throws CordaPersistenceException If an error occurs during execution.
     */
    @Suspendable
    @Nullable
    <T> T find(@NotNull Class<T> entityClass, @NotNull Object primaryKey);

    /**
     * Finds multiple entities of the same type with different primary keys in a single transaction.
     *
     * @param entityClass The type of the entities to find.
     * @param primaryKeys List of primary keys to find with the given {@code entityClass} type.
     *
     * @return List of entities found. Empty list if none were found.
     *
     * @throws IllegalArgumentException If {@code entityClass} is a primitive type.
     * @throws CordaPersistenceException If an error occurs during execution.
     */
    @Suspendable
    @NotNull
    <T> List<T> find(@NotNull Class<T> entityClass, @NotNull List<?> primaryKeys);

    /**
     * Creates a {@link PagedQuery} to find all entities of the same type from the persistence context in a single transaction.
     *
     * @param entityClass The type of the entities to find.
     * @return A {@link PagedQuery} That returns the list of entities found.
     *
     * @throws IllegalArgumentException If {@code entityClass} is a primitive type.
     * @throws CordaPersistenceException If an error occurs during execution.
     */
    @Suspendable
    @NotNull
    <T> PagedQuery<T> findAll(@NotNull Class<T> entityClass);

    /**
     * Creates a {@code ParameterizedQuery} to support a named query to return a list of entities of the given type in a
     * single transaction. Casts result set to the specified type [T].
     * <p>
     * Example usage:
     * <ul>
     * <li>Kotlin:<pre>{@code
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
     * }</pre></li>
     * <li>Java:<pre>{@code
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
     * ParameterizedQuery<Dog> pagedQuery = persistenceService
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
     * ParameterizedQuery<Dog> paramQuery = persistenceService
     *         .query("find_by_name_and_age", Dog.class)
     *         .setParameters(Map.of("name", "Felix", "maxAge", 5))
     *         .setLimit(100)
     *         .setOffset(200);
     *
     * // execute the query and return the results as a List
     * List<Dog> result2 = pagedQuery.execute();
     * }</pre></li>
     * </ul>
     * @param queryName The name of the named query registered in the persistence context.
     * @param entityClass The type of the entities to find.
     * @param <T> The type of the results.
     *
     * @return A {@link ParameterizedQuery} that returns the list of entities found. Empty list if none were found.
     *
     * @throws IllegalArgumentException If {@code entityClass} is a primitive type.
     */
    @Suspendable
    @NotNull
    <T> ParameterizedQuery<T> query(
        @NotNull String queryName,
        @NotNull Class<T> entityClass
    );
}
