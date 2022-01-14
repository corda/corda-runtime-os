package net.corda.orm.utils

import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

/**
 * Creates an [EntityManager], executes the [block] and closes the [EntityManager].
 *
 * Starting and committing the [EntityManager]'s transaction is up to the [block].
 *
 * @param block The code to execute using the [EntityManager].
 * @param R The type returned by [block].
 *
 * @return The result of executing [block].
 *
 * @see transaction
 */
inline fun <R> EntityManagerFactory.use(block: (EntityManager) -> R): R {
    return createEntityManager().use(block)
}

/**
 * Executes the [block] and closes the [EntityManager].
 *
 * Starting and committing the [EntityManager]'s transaction is up to the [block].
 *
 * @param block The code to execute using the [EntityManager].
 * @param R The type returned by [block].
 *
 * @return The result of executing [block].
 *
 * @see transaction
 */
inline fun <R> EntityManager.use(block: (EntityManager) -> R): R {
    return try {
        block(this)
    } finally {
        close()
    }
}

/**
 * Creates a new [EntityManager], begins a new transaction, commits it and then closes it after executing the [block].
 *
 * If an error occurs and the transaction is marked as "rollbackOnly" and is rolled back instead of committed.
 *
 * @param block The code to execute before committing the [EntityManager]'s transaction.
 * @param R The type returned by [block].
 *
 * @return The result of executing [block].
 *
 * @see use
 */
inline fun <R> EntityManagerFactory.transaction(block: (EntityManager) -> R): R {
    return createEntityManager().transaction(block)
}

/**
 * Begins a new transaction, commits it and then closes it after executing the [block].
 *
 * If an error occurs and the transaction is marked as "rollbackOnly" and is rolled back instead of committed.
 *
 * @param block The code to execute before committing the [EntityManager]'s transaction.
 * @param R The type returned by [block].
 *
 * @return The result of executing [block].
 *
 * @see use
 */
inline fun <R> EntityManager.transaction(block: (EntityManager) -> R): R {
    val currentTransaction = transaction
    currentTransaction.begin()

    return try {
        block(this)
    } catch (e: Exception) {
        currentTransaction.setRollbackOnly()
        throw e
    } finally {
        if (!currentTransaction.rollbackOnly) {
            currentTransaction.commit()
        } else {
            currentTransaction.rollback()
        }
        close()
    }
}