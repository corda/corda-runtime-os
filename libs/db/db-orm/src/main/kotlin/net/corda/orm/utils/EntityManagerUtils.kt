package net.corda.orm.utils

import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

/**
 *
 * DEPRECATED due to confusing semantics which are not idiomatic in Kotlin.
 * You probably want consume instead.
 *
 * Creates an [EntityManager], executes the [block] and closes the [EntityManager].
 *
 * NOTE: this does not close the EntityManagerFactory, which is different to the semantics
 * of use in the Kotlin standard library.
 *
 * Starting and committing the [EntityManager]'s transaction is up to the [block].
 *
 * @param block The code to execute using the [EntityManager].
 * @param R The type returned by [block].
 *
 * @return The result of executing [block].
 *
 * @see transaction
 * @see transactionStep
 * @see consume
 */
inline fun <R> EntityManagerFactory.use(block: (EntityManager) -> R): R {
    return createEntityManager().use(block)
}


/**
 * Creates an [EntityManager], executes the [block] with the [EntityManager] as the receiver,
 * and closes the [EntityManager] and the [EntityManagerFactory] itself.
 *
 * Starting and committing the [EntityManager]'s transaction is up to the [block].
 *
 * @param block The code to execute using the [EntityManager].
 * @param R The type returned by [block].
 *
 * @return The result of executing [block].
 *
 * @see transaction
 * @see transactionStep
 * @see use
 */
inline fun <R> EntityManagerFactory.consume(block: EntityManager.() -> R): R {
    try {
        return createEntityManager().use { it.block() }
    } finally {
        this.close()
    }
}


/**
 * Creates an [EntityManager], executes the [block] with the [EntityManager] as the receiver in a transaction,
 * and closes the [EntityManager] and the [EntityManagerFactory] itself.
 **
 * @param block The code to execute using the [EntityManager].
 * @param R The type returned by [block].
 *
 * @return The result of executing [block].
 *
 * @see transaction
 */
inline fun <R> EntityManagerFactory.consumeTransaction(block: EntityManager.() -> R): R {
    try {
        return createEntityManager().use {
            transaction {
                it.block()
            }
        }
    } finally {
        this.close()
    }
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
 *
 * DEPRECATED - you probably want transactionStep instead, since this closes the parent EntityManager.
 *
 * Begins a new transaction, commits it and then closes the transaction and the parent entity manager
 * after executing the [block].
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
    // delegate to non-extension method so that it can be tested
    return transactionExecutor(this, block)
}


/**
 * Begins a new transaction, commits it and then closes it after executing the [block]. Does not close the
 * parent [EntityManager]
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
inline fun <R> EntityManager.transactionStep(block: EntityManager.() -> R): R {
    val currentTransaction = transaction
    currentTransaction.begin()
    try {
        return block(this)
    } catch (e: Exception) {
        currentTransaction.setRollbackOnly()
        throw e
    } finally {
        if (!currentTransaction.rollbackOnly) {
            currentTransaction.commit()
        } else {
            currentTransaction.rollback()
        }
    }
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
 * @see transaction
 */
inline fun <R> transactionExecutor(entityManager: EntityManager, block: (EntityManager) -> R): R {
    entityManager.use { em ->
        val currentTransaction = em.transaction
        currentTransaction.begin()

        return try {
            block(em)
        } catch (e: Exception) {
            currentTransaction.setRollbackOnly()
            throw e
        } finally {
            if (!currentTransaction.rollbackOnly) {
                currentTransaction.commit()
            } else {
                currentTransaction.rollback()
            }
        }
    }
}