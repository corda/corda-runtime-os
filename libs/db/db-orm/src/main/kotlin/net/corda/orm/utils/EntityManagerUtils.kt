package net.corda.orm.utils

import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction

/**
 * Transaction data stored in [ThreadLocal]
 *
 * @param blockCount Nesting level of transaction blocks
 * @param entityManager [EntityManager] used in all transaction blocks
 * @param transaction [EntityTransaction] used in all transaction blocks
 */
class ThreadLoc(var blockCount: Int, var entityManager: EntityManager?, var transaction: EntityTransaction?) {
    companion object {
        val data = ThreadLocal<ThreadLoc>()
    }
}

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
    val threadLoc = ThreadLoc.data.get()
    val entityManager =
        if (threadLoc == null) createEntityManager()
        else threadLoc.entityManager ?: createEntityManager().apply { threadLoc.entityManager = this }
    val transaction =
        if (threadLoc == null) entityManager.transaction.apply { begin() }
        else threadLoc.transaction ?:
            entityManager.transaction.apply {
                threadLoc.transaction = this
                begin()
            }

    return try {
        block(entityManager)
    } catch (e: Exception) {
        if (threadLoc == null) transaction.setRollbackOnly()
        throw e
    } finally {
        if (threadLoc == null) {
            if (!transaction.rollbackOnly) {
                transaction.commit()
            } else {
                transaction.rollback()
            }
            entityManager.close()
        }
    }
}

/**
 * Defines block for which all enclosing blocks will use the same [EntityManager] and [EntityTransaction].
 * The first [EntityManger] is being used, begins a new transaction, commits it and then closes it after executing the [block].
 * [EntityManager] and [EntityTransaction] are stored in a [ThreadLocal] variable.
 * If an error occurs and the transaction is marked as "rollbackOnly" and is rolled back instead of committed.
 *
 * @param block The code to execute before committing the [EntityManager]'s transaction.
 * @param R The type returned by [block].
 *
 * @return The result of executing [block].
 */
inline fun <R> transaction(block: () -> R): R {
    val threadLoc = ThreadLoc.data.get() ?:
        ThreadLoc(0, null, null).apply { ThreadLoc.data.set(this) }
    return try {
        threadLoc.blockCount++
        block()
    } catch (e: Exception) {
        if (threadLoc.blockCount == 1) {
            threadLoc.transaction?.setRollbackOnly()
        }
        throw e
    } finally {
        if (threadLoc.blockCount == 1) {
            threadLoc.transaction?.let {
                if (!it.rollbackOnly) {
                    it.commit()
                } else {
                    it.rollback()
                }
                threadLoc.transaction = null
            }
            threadLoc.entityManager?.let {
                it.close()
                threadLoc.entityManager = null
            }
            ThreadLoc.data.remove()
        } else threadLoc.blockCount--
    }
}
