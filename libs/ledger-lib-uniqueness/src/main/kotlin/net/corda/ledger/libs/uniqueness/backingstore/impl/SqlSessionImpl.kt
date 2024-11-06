package net.corda.ledger.libs.uniqueness.backingstore.impl

import net.corda.ledger.libs.uniqueness.UniquenessSecureHashFactory
import net.corda.ledger.libs.uniqueness.backingstore.BackingStore
import net.corda.ledger.libs.uniqueness.backingstore.BackingStoreMetricsFactory
import net.corda.ledger.libs.uniqueness.data.UniquenessHoldingIdentity
import net.corda.orm.PersistenceExceptionCategorizer
import net.corda.orm.PersistenceExceptionType
import net.corda.uniqueness.datamodel.common.UniquenessConstants.RESULT_ACCEPTED_REPRESENTATION
import net.corda.uniqueness.datamodel.common.UniquenessConstants.RESULT_REJECTED_REPRESENTATION
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultFailureImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultSuccessImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckStateDetailsImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckStateRefImpl
import net.corda.uniqueness.datamodel.internal.UniquenessCheckTransactionDetailsInternal
import net.corda.v5.application.uniqueness.model.UniquenessCheckError
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateDetails
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateRef
import net.corda.v5.crypto.SecureHash
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.Duration
import java.util.Calendar
import java.util.TimeZone

@Suppress("LongParameterList")
class SqlSessionImpl(
    private val holdingIdentity: UniquenessHoldingIdentity,
    private val connection: Connection,
    private val backingStoreMetricsFactory: BackingStoreMetricsFactory,
    private val persistenceExceptionCategorizer: PersistenceExceptionCategorizer,
    private val uniquenessSecureHashFactory: UniquenessSecureHashFactory,
    private val sqlQueryProvider: SqlQueryProvider = DefaultSqlQueryProvider()
) : BackingStore.Session {
    private companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val MAX_ATTEMPTS = 10
        private val tzUTC: Calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    }

    init {
        connection.autoCommit = false
    }

    private val transactionOps =
        SqlTransactionOpsImpl(
            connection,
            sqlQueryProvider,
            uniquenessSecureHashFactory,
            backingStoreMetricsFactory,
            holdingIdentity
        )

    @Suppress("NestedBlockDepth")
    override fun executeTransaction(block: (BackingStore.Session, BackingStore.Session.TransactionOps) -> Unit) {
        val transactionStartTime = System.nanoTime()

        try {
            for (attemptNumber in 1..MAX_ATTEMPTS) {
                try {
                    block(this, transactionOps)
                    connection.commit()

                    backingStoreMetricsFactory.recordTransactionAttempts(
                        attemptNumber,
                        holdingIdentity
                    )
                    return
                } catch (e: Exception) {
                    when (persistenceExceptionCategorizer.categorize(e)) {
                        PersistenceExceptionType.DATA_RELATED,
                        PersistenceExceptionType.TRANSIENT -> {
                            // [ConsumeStateFailedException] Occurs when another worker committed a
                            // request with conflicting input states. Retry (by not re-throwing the
                            // exception), because the requests with conflicts are removed from the
                            // batch by the code passed in as `block`.

                            // TODO This is needed because some of the exceptions
                            //  we retry do not roll the transaction back. Once
                            //  we improve our error handling in CORE-4983 this
                            //  won't be necessary
                            if (!connection.isClosed && !connection.autoCommit) {
                                connection.rollback()
                                log.warn("Rolled back transaction")
                            }
                            backingStoreMetricsFactory.incrementTransactionErrorCount(e, holdingIdentity)

                            if (attemptNumber < MAX_ATTEMPTS) {
                                log.warn(
                                    "Retrying DB operation. The request might have been " +
                                        "handled by a different notary worker or a DB error " +
                                        "occurred when attempting to commit. Message: ${e.message}."
                                )
                            } else {
                                throw IllegalStateException(
                                    "Failed to execute transaction after the maximum number of " +
                                        "attempts (${MAX_ATTEMPTS}). Message: ${e.message}."
                                )
                            }
                        }
                        PersistenceExceptionType.UNCATEGORIZED, PersistenceExceptionType.FATAL -> {
                            log.warn("Unexpected error occurred. Message: ${e.message}")
                            // We potentially leak a database connection, if we don't rollback. When
                            // the HSM signing operation throws an exception this code path is
                            // triggered.
                            if (!connection.isClosed && !connection.autoCommit) {
                                connection.rollback()
                                log.warn("Rolled back transaction")
                            }
                            backingStoreMetricsFactory.incrementTransactionErrorCount(e, holdingIdentity)

                            throw e
                        }
                    }
                }
            }
        } finally {
            backingStoreMetricsFactory.recordTransactionExecutionTime(
                Duration.ofNanos(System.nanoTime() - transactionStartTime),
                holdingIdentity
            )
        }
    }

    @Suppress("NestedBlockDepth")
    override fun getStateDetails(states: Collection<UniquenessCheckStateRef>): Map<UniquenessCheckStateRef, UniquenessCheckStateDetails> {
        val queryStartTime = System.nanoTime()

        val results = HashMap<
            UniquenessCheckStateRef,
            UniquenessCheckStateDetails
            >()

        val statePks = states.map {
            UniquenessTxAlgoStateRefKey(it.txHash.algorithm, uniquenessSecureHashFactory.getBytes(it.txHash), it.stateIndex)
        }

        connection.prepareStatement(sqlQueryProvider.findStatesByKeyQuery()).use { stmt ->
            stmt.setObject(1, statePks.map { it.issueTxIdAlgo }.toTypedArray())
            stmt.setObject(2, statePks.map { it.issueTxId }.toTypedArray())
            stmt.setObject(3, statePks.map { it.issueTxOutputIndex }.toTypedArray())
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    val selectIssueTxIdAlgo = rs.getString(1)
                    val selectIssueTxId = rs.getBytes(2)
                    val selectConsumingTxId = rs.getObject(5)
                    val consumingTxId =
                        if (rs.wasNull()) {
                            null
                        } else {
                            val selectConsumingTxIdAlgo = rs.getString(4)
                            uniquenessSecureHashFactory.createSecureHash(selectConsumingTxIdAlgo!!, selectConsumingTxId as ByteArray)
                        }

                    val selectIssueTxOutputIndex = rs.getInt(3)

                    val returnedState = UniquenessCheckStateRefImpl(
                        uniquenessSecureHashFactory.createSecureHash(selectIssueTxIdAlgo, selectIssueTxId),
                        selectIssueTxOutputIndex
                    )
                    results[returnedState] = UniquenessCheckStateDetailsImpl(returnedState, consumingTxId)
                }
            }
        }

        backingStoreMetricsFactory.recordDatabaseReadTime(
            Duration.ofNanos(System.nanoTime() - queryStartTime),
            holdingIdentity
        )
        return results
    }

    @Suppress("NestedBlockDepth")
    override fun getTransactionDetails(txIds: Collection<SecureHash>): Map<out SecureHash, UniquenessCheckTransactionDetailsInternal> {
        val queryStartTime = System.nanoTime()

        val txPks = txIds.map {
            UniquenessTxAlgoIdKey(it.algorithm, uniquenessSecureHashFactory.getBytes(it))
        }

        val results = mutableMapOf<SecureHash, UniquenessCheckTransactionDetailsInternal>()

        connection.prepareStatement(sqlQueryProvider.findTransactionDetailByKeyQuery()).use { stmt ->
            stmt.setObject(1, txPks.map { it.txIdAlgo }.toTypedArray())
            stmt.setObject(2, txPks.map { it.txId }.toTypedArray())
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    val txIdAlgo = rs.getString(1)
                    val txId = rs.getBytes(2)
                    val txResult = rs.getString(3).first()
                    val txCommitTimestamp = rs.getTimestamp(4, tzUTC).toInstant()

                    val result = when (txResult) {
                        RESULT_ACCEPTED_REPRESENTATION -> {
                            UniquenessCheckResultSuccessImpl(txCommitTimestamp)
                        }
                        RESULT_REJECTED_REPRESENTATION -> {
                            // If the transaction is rejected we need to make sure it is also
                            // stored in the rejected tx table
                            UniquenessCheckResultFailureImpl(
                                txCommitTimestamp,
                                getTransactionError(txIdAlgo, txId) ?: throw IllegalStateException(
                                    "Transaction with id $txId was rejected but no records were " +
                                        "found in the rejected transactions table"
                                )
                            )
                        }
                        else -> throw IllegalStateException(
                            "Transaction result can only be " +
                                "'$RESULT_ACCEPTED_REPRESENTATION' or '$RESULT_REJECTED_REPRESENTATION'"
                        )
                    }
                    val txHash = uniquenessSecureHashFactory.createSecureHash(txIdAlgo, txId)
                    results[txHash] = UniquenessCheckTransactionDetailsInternal(txHash, result)
                }
            }
        }

        backingStoreMetricsFactory.recordDatabaseReadTime(
            Duration.ofNanos(System.nanoTime() - queryStartTime),
            holdingIdentity
        )
        return results
    }

    private fun getTransactionError(
        txIdAlgo: String,
        txId: ByteArray,
    ): UniquenessCheckError? {
        val queryStartTime = System.nanoTime()

        return connection.prepareStatement(sqlQueryProvider.findRejectedTransactionQuery()).use { stmt ->
            stmt.setString(1, txIdAlgo)
            stmt.setBytes(2, txId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    jpaBackingStoreObjectMapper(uniquenessSecureHashFactory).readValue(
                        rs.getBytes(1),
                        UniquenessCheckError::class.java
                    )
                } else {
                    null
                }
            }.also {
                backingStoreMetricsFactory.recordDatabaseReadTime(
                    Duration.ofNanos(System.nanoTime() - queryStartTime),
                    holdingIdentity
                )
            }
        }
    }
}
