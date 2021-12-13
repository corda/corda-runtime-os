package net.corda.orm.utils

import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

/**
 * Creates a new [EntityManager], begins a new transaction and closes it after executing the [block].
 *
 * Committing the transaction is up to the [block].
 *
 * @param block The code to execute within the [EntityManager]'s transaction.
 * @param R The type returned by [block].
 *
 * @return The result of executing [block].
 *
 * @see commit
 */
inline fun <R> EntityManagerFactory.transaction(block: (EntityManager) -> R): R {
    return createEntityManager().transaction(block)
}

/**
 * Begins a new transaction and closes it after executing the [block].
 *
 * Committing the transaction is up to the [block].
 *
 * @param block The code to execute within the [EntityManager]'s transaction.
 * @param R The type returned by [block].
 *
 * @return The result of executing [block].
 *
 * @see commit
 */
inline fun <R> EntityManager.transaction(block: (EntityManager) -> R): R {
    transaction.begin()
    return try {
        block(this)
    } finally {
        close()
    }
}

/**
 * Creates a new [EntityManager], begins a new transaction, commits it and then closes it after executing the [block].
 *
 * If an error occurs and the transaction is marked as "rollbackOnly" then it will be rolled back instead of committed.
 *
 * @param block The code to execute before committing the [EntityManager]'s transaction.
 * @param R The type returned by [block].
 *
 * @return The result of executing [block].
 *
 * @see transaction
 */
inline fun <R> EntityManagerFactory.commit(block: (EntityManager) -> R): R {
    return createEntityManager().commit(block)
}

/**
 * Begins a new transaction, commits it and then closes it after executing the [block].
 *
 * If an error occurs and the transaction is marked as "rollbackOnly" then it will be rolled back instead of committed.
 *
 * @param block The code to execute before committing the [EntityManager]'s transaction.
 * @param R The type returned by [block].
 *
 * @return The result of executing [block].
 *
 * @see transaction
 */
inline fun <R> EntityManager.commit(block: (EntityManager) -> R): R {
    transaction.begin()
    return try {
        block(this)
    } finally {
        if (!transaction.rollbackOnly) {
            transaction.commit()
        } else {
            transaction.rollback()
        }
        close()
    }
}