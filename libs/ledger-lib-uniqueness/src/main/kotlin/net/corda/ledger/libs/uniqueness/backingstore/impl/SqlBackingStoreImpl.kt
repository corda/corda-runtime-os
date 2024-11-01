package net.corda.ledger.libs.uniqueness.backingstore.impl

import net.corda.ledger.libs.uniqueness.UniquenessSecureHashFactory
import net.corda.ledger.libs.uniqueness.backingstore.BackingStore
import net.corda.ledger.libs.uniqueness.backingstore.BackingStoreMetricsFactory
import net.corda.ledger.libs.uniqueness.data.UniquenessHoldingIdentity
import net.corda.orm.PersistenceExceptionCategorizer
import java.sql.Connection
import java.time.Duration

/**
 * Backing store using "plain" SQL.
 * This could be further specialised for different types of DBs if necessary.
 */
class SqlBackingStoreImpl(
    private val connectionFactory: (holdingIdentity: UniquenessHoldingIdentity) -> Connection,
    private val backingStoreMetricsFactory: BackingStoreMetricsFactory,
    private val uniquenessSecureHashFactory: UniquenessSecureHashFactory,
    private val persistenceExceptionCategorizer: PersistenceExceptionCategorizer = SqlPersistenceExceptionCategorizerImpl()
) : BackingStore {
    override fun session(holdingIdentity: UniquenessHoldingIdentity, block: (BackingStore.Session) -> Unit) {
        val sessionStartTime = System.nanoTime()

//        // Enable Hibernate JDBC batch and set the batch size on a per-session basis.
//        entityManager.unwrap(Session::class.java).jdbcBatchSize = HIBERNATE_JDBC_BATCH_SIZE
        val connection = connectionFactory(holdingIdentity)
        @Suppress("TooGenericExceptionCaught")
        try {
            block(
                SqlSessionImpl(
                    holdingIdentity,
                    connection,
                    backingStoreMetricsFactory,
                    persistenceExceptionCategorizer,
                    uniquenessSecureHashFactory
                )
            )
            connection.close()
        } catch (e: Exception) {
            connection.close()
            throw e
        } finally {
            backingStoreMetricsFactory.recordSessionExecutionTime(
                Duration.ofNanos(System.nanoTime() - sessionStartTime),
                holdingIdentity
            )
        }
    }
}
