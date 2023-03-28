package net.corda.crypto.softhsm.impl

import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use


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

