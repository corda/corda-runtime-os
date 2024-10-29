package net.corda.ledger.libs.uniqueness.backingstore.impl

import net.corda.ledger.libs.uniqueness.UniquenessSecureHashFactory
import net.corda.ledger.libs.uniqueness.backingstore.BackingStore
import net.corda.ledger.libs.uniqueness.backingstore.BackingStoreMetricsFactory
import net.corda.ledger.libs.uniqueness.data.UniquenessHoldingIdentity
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
import javax.persistence.EntityExistsException

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
            println("######## $stmt")
            stmt.executeBatch()
        }
    }

    override fun consumeStates(consumingTxId: SecureHash, stateRefs: Collection<UniquenessCheckStateRef>) {
        connection.prepareStatement(sqlQueryProvider.consumeStatesQuery()).use { stmt ->
            stateRefs.forEach { stateRef ->
                stmt.setString(1, consumingTxId.algorithm)
                stmt.setBytes(2, uniquenessSecureHashFactory.getBytes(consumingTxId))
                stmt.setString(3, stateRef.txHash.algorithm)
                stmt.setBytes(4, uniquenessSecureHashFactory.getBytes(stateRef.txHash))
                stmt.setInt(5, stateRef.stateIndex)
                stmt.addBatch()
            }
            println("######## $stmt")
            val updatedRowCount = stmt.executeBatch().sum()
            if (updatedRowCount == 0) {
                // TODO: Figure out application specific exceptions
                throw EntityExistsException(
                    "No states were consumed, this might be an in-flight double spend"
                )
            }
        }
    }

    override fun commitTransactions(transactionDetails: Collection<Pair<UniquenessCheckRequestInternal, UniquenessCheckResult>>) {
        val commitStartTime = System.nanoTime()

        connection.prepareStatement(sqlQueryProvider.insertTransactionDetailsQuery()).use { stmt ->
            connection.prepareStatement(sqlQueryProvider.insertRejectedTransactionQuery()).use { rejectStmt ->
                transactionDetails.forEach { (request, result) ->
                    stmt.setString(1, request.txId.algorithm)
                    stmt.setBytes(2, uniquenessSecureHashFactory.getBytes(request.txId))
                    stmt.setString(3, request.originatorX500Name)
                    stmt.setTimestamp(4, Timestamp.from(request.timeWindowUpperBound), tzUTC)
                    stmt.setTimestamp(5, Timestamp.from(result.resultTimestamp), tzUTC)
                    stmt.setString(6, result.toCharacterRepresentation().toString())
                    stmt.addBatch()

                    if (result is UniquenessCheckResultFailure) {
                        rejectStmt.setString(1, request.txId.algorithm)
                        rejectStmt.setBytes(2, uniquenessSecureHashFactory.getBytes(request.txId))
                        rejectStmt.setBytes(
                            3,
                            jpaBackingStoreObjectMapper(uniquenessSecureHashFactory).writeValueAsBytes(result.error)
                        )
                        rejectStmt.addBatch()
                    }
                }
                println("######## $stmt")
                stmt.executeBatch()
                println("######## $rejectStmt")
                rejectStmt.executeBatch()
            }
        }
        backingStoreMetricsFactory.recordDatabaseCommitTime(
            Duration.ofNanos(System.nanoTime() - commitStartTime),
            holdingIdentity
        )
    }
}
