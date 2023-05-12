package net.corda.orm.utils

import net.corda.utilities.time.UTCClock
import org.slf4j.LoggerFactory
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

/**
 * Executes the [block] and then closes the [EntityManagerFactory].
 *
 * NOTE: because EntityManagerFactory does not implement [AutoCloseable] it doesn't support 'use' natively.
 *
 * @param block The code to execute using the [EntityManager].
 * @param R The type returned by [block].
 *
 * @return The result of executing [block].
 *
 * @see transaction
 */
inline fun <R> EntityManagerFactory.use(block: (EntityManagerFactory) -> R): R {
    return try {
        block(this)
    } finally {
        close()
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
    val start = clock.instant()
    val id = UUID.randomUUID()
    logger.info(
        "DB investigation " +
                "- inline fun <R> EntityManagerFactory.transaction(block: (EntityManager) -> R): R " +
                "- 1 " +
                "- $id " +
                "- ${clock.instant().nano} "
    )
    return createEntityManager().also{
        logger.info(
            "DB investigation " +
                    "- inline fun <R> EntityManagerFactory.transaction(block: (EntityManager) -> R): R " +
                    "- 2 " +
                    "- $id " +
                    "- ${clock.instant().nano} "
        )
    }.transaction(block).also {
        logger.info(
            "DB investigation " +
                    "- inline fun <R> EntityManagerFactory.transaction(block: (EntityManager) -> R): R " +
                    "- total " +
                    "- $id " +
                    "- ${clock.instant().minusNanos(start.nano.toLong()).nano}"
        )
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
 * @see use
 */
inline fun <R> EntityManager.transaction(block: (EntityManager) -> R): R {
    // delegate to non-extension method so that it can be tested
    return transactionExecutor(this, block)
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
    val start = clock.instant()
    val id = UUID.randomUUID()
    entityManager.use { em ->
        logger.info(
            "DB investigation " +
                    "- inline fun <R> transactionExecutor(entityManager: EntityManager, block: (EntityManager) -> R): R " +
                    "- 1 " +
                    "- $id " +
                    "- ${clock.instant().nano} "
        )
        val currentTransaction = em.transaction
        logger.info(
            "DB investigation " +
                    "- inline fun <R> transactionExecutor(entityManager: EntityManager, block: (EntityManager) -> R): R " +
                    "- 2 " +
                    "- $id " +
                    "- ${clock.instant().nano} "
        )
        currentTransaction.begin()
        logger.info(
            "DB investigation " +
                    "- inline fun <R> transactionExecutor(entityManager: EntityManager, block: (EntityManager) -> R): R " +
                    "- 3 " +
                    "- $id " +
                    "- ${clock.instant().nano} "
        )
        return try {
            block(em).also {
                logger.info(
                    "DB investigation " +
                            "- inline fun <R> transactionExecutor(entityManager: EntityManager, block: (EntityManager) -> R): R " +
                            "- 4 " +
                            "- $id " +
                            "- ${clock.instant().nano} "
                )
            }
        } catch (e: Exception) {
            currentTransaction.setRollbackOnly()
            throw e
        } finally {
            logger.info(
                "DB investigation " +
                        "- inline fun <R> transactionExecutor(entityManager: EntityManager, block: (EntityManager) -> R): R " +
                        "- 5 " +
                        "- $id " +
                        "- ${clock.instant().nano} "
            )
            if (!currentTransaction.rollbackOnly) {
                currentTransaction.commit()
            } else {
                currentTransaction.rollback()
            }
            logger.info(
                "DB investigation " +
                    "- inline fun <R> transactionExecutor(entityManager: EntityManager, block: (EntityManager) -> R): R " +
                    "- 6 " +
                    "- $id " +
                    "- ${clock.instant().nano} "
            )
            logger.info(
                "DB investigation " +
                        "- inline fun <R> transactionExecutor(entityManager: EntityManager, block: (EntityManager) -> R): R " +
                        "- total " +
                        "- $id " +
                        "- ${clock.instant().minusNanos(start.nano.toLong()).nano}"
            )
        }
    }
}

val clock = UTCClock()
val logger = LoggerFactory.getLogger("EntityManagerUtils")