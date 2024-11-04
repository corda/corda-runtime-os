package net.corda.ledger.libs.uniqueness.backingstore.impl

import net.corda.crypto.core.bytes
import net.corda.ledger.libs.uniqueness.UniquenessSecureHashFactory
import net.corda.ledger.libs.uniqueness.backingstore.BackingStore
import net.corda.ledger.libs.uniqueness.backingstore.BackingStoreMetricsFactory
import net.corda.ledger.libs.uniqueness.backingstore.ConsumeStateFailedException
import net.corda.ledger.libs.uniqueness.data.UniquenessHoldingIdentity
import net.corda.uniqueness.datamodel.common.UniquenessConstants.REJECTED_TRANSACTION_ERROR_DETAILS_LENGTH
import net.corda.uniqueness.datamodel.common.toCharacterRepresentation
import net.corda.uniqueness.datamodel.internal.UniquenessCheckRequestInternal
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultFailure
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateRef
import net.corda.v5.crypto.SecureHash
import java.sql.Connection
import java.sql.Timestamp
import java.time.Duration
import java.util.Calendar
import java.util.TimeZone

// TODO - integration test these queries in isolation
class SqlTransactionOpsImpl(
    private val connection: Connection,
    private val sqlQueryProvider: SqlQueryProvider,
    private val uniquenessSecureHashFactory: UniquenessSecureHashFactory,
    private val backingStoreMetricsFactory: BackingStoreMetricsFactory,
    private val holdingIdentity: UniquenessHoldingIdentity,
) : BackingStore.Session.TransactionOps {
    companion object {
        val tzUTC: Calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    }
    override fun createUnconsumedStates(stateRefs: Collection<UniquenessCheckStateRef>) {
        connection.prepareStatement(sqlQueryProvider.insertUnconsumedStatesQuery()).use { stmt ->
            stateRefs.forEach { stateRef ->
                stmt.setString(1, stateRef.txHash.algorithm)
                stmt.setBytes(2, uniquenessSecureHashFactory.getBytes(stateRef.txHash))
                stmt.setInt(3, stateRef.stateIndex)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    override fun consumeStates(consumingTxId: SecureHash, stateRefs: Collection<UniquenessCheckStateRef>) {
        connection.prepareStatement(sqlQueryProvider.consumeStatesQuery()).use { stmt ->
            stateRefs.forEach { stateRef ->
                stmt.setString(1, consumingTxId.algorithm)
                stmt.setBytes(2, consumingTxId.bytes)
                stmt.setString(3, stateRef.txHash.algorithm)
                stmt.setBytes(4, stateRef.txHash.bytes)
                stmt.setInt(5, stateRef.stateIndex)

                // Not using batch insert so we can check each query has done an update.
                // this replicates existing behaviour, but could be further optimised!
                val updatedRowCount = stmt.executeUpdate()
                if (updatedRowCount == 0) {
                    throw ConsumeStateFailedException(
                        "No states were consumed, this might be an in-flight double spend"
                    )
                }
            }
        }
    }

    @Suppress("NestedBlockDepth")
    override fun commitTransactions(transactionDetails: Collection<Pair<UniquenessCheckRequestInternal, UniquenessCheckResult>>) {
        val commitStartTime = System.nanoTime()

        connection.prepareStatement(sqlQueryProvider.insertTransactionDetailsQuery()).use { stmt ->
            connection.prepareStatement(sqlQueryProvider.insertRejectedTransactionQuery()).use { rejectStmt ->
                transactionDetails.forEach { (request, result) ->
                    stmt.setString(1, request.txId.algorithm)
                    stmt.setBytes(2, request.txId.bytes)
                    stmt.setString(3, request.originatorX500Name)
                    stmt.setTimestamp(4, Timestamp.from(request.timeWindowUpperBound), tzUTC)
                    stmt.setTimestamp(5, Timestamp.from(result.resultTimestamp), tzUTC)
                    stmt.setString(6, result.toCharacterRepresentation().toString())
                    stmt.addBatch()

                    if (result is UniquenessCheckResultFailure) {
                        val errorDetails = backingStoreObjectMapper(uniquenessSecureHashFactory).writeValueAsBytes(result.error)
                        // NOTE: this limitation is put in to replicate the existing behaviour, but this is un-necessary.
                        //  The type of VARBINARY(1024) as set in Liquibase, does not exist in PostgeSQL, and instead a BYTEA is used
                        //  which fits 1Gb of space.
                        if (errorDetails.size > REJECTED_TRANSACTION_ERROR_DETAILS_LENGTH) {
                            throw IllegalArgumentException(
                                "The maximum size of the error_details field is $REJECTED_TRANSACTION_ERROR_DETAILS_LENGTH"
                            )
                        }
                        rejectStmt.setString(1, request.txId.algorithm)
                        rejectStmt.setBytes(2, request.txId.bytes)
                        rejectStmt.setBytes(3, errorDetails)
                        rejectStmt.addBatch()
                    }
                }
                stmt.executeBatch()
                rejectStmt.executeBatch()
            }
        }
        backingStoreMetricsFactory.recordDatabaseCommitTime(
            Duration.ofNanos(System.nanoTime() - commitStartTime),
            holdingIdentity
        )
    }
}
