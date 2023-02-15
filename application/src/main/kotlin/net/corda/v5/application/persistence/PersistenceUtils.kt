@file:JvmName("PersistenceUtils")
package net.corda.v5.application.persistence

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
