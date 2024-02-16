package net.corda.uniqueness.backingstore.impl

import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.bytes
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.libs.virtualnode.datamodel.VirtualNodeEntities
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepository
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepositoryImpl
import net.corda.metrics.CordaMetrics
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.utils.use
import net.corda.uniqueness.backingstore.BackingStore
import net.corda.uniqueness.datamodel.common.UniquenessConstants.HIBERNATE_JDBC_BATCH_SIZE
import net.corda.uniqueness.datamodel.common.UniquenessConstants.RESULT_ACCEPTED_REPRESENTATION
import net.corda.uniqueness.datamodel.common.UniquenessConstants.RESULT_REJECTED_REPRESENTATION
import net.corda.uniqueness.datamodel.common.toCharacterRepresentation
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultFailureImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultSuccessImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckStateDetailsImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckStateRefImpl
import net.corda.uniqueness.datamodel.internal.UniquenessCheckRequestInternal
import net.corda.uniqueness.datamodel.internal.UniquenessCheckTransactionDetailsInternal
import net.corda.utilities.debug
import net.corda.v5.application.uniqueness.model.UniquenessCheckError
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultFailure
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateDetails
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateRef
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import org.hibernate.Session
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import javax.persistence.EntityExistsException
import javax.persistence.EntityManager
import javax.persistence.OptimisticLockException
import javax.persistence.RollbackException

@Suppress("ForbiddenComment")
/**
 * JPA backing store implementation, which uses a JPA compliant database to persist data.
 */
@Component(service = [BackingStore::class])
open class JPABackingStoreImpl(
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
    private val dbConnectionManager: DbConnectionManager,
    private val virtualNodeRepository: VirtualNodeRepository)  : BackingStore {

    @Activate
    constructor(
        @Reference(service = JpaEntitiesRegistry::class)
        jpaEntitiesRegistry: JpaEntitiesRegistry,
        @Reference(service = DbConnectionManager::class)
        dbConnectionManager: DbConnectionManager,
    ) : this(jpaEntitiesRegistry, dbConnectionManager, VirtualNodeRepositoryImpl())

    private companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        // TODO: Replace constants with config
        const val MAX_ATTEMPTS = 10
    }

    init {
        jpaEntitiesRegistry.register(
            CordaDb.Uniqueness.persistenceUnitName,
            JPABackingStoreEntities.classes
        )
        jpaEntitiesRegistry.register(
            CordaDb.CordaCluster.persistenceUnitName,
            VirtualNodeEntities.classes
        )
    }

    override fun session(holdingIdentity: HoldingIdentity, block: (BackingStore.Session) -> Unit) {

        val sessionStartTime = System.nanoTime()

        val virtualNodeInfo = dbConnectionManager.getClusterEntityManagerFactory().createEntityManager().use {em ->
            virtualNodeRepository.find(em, holdingIdentity.shortHash)
        }
        requireNotNull(virtualNodeInfo) {"virtualNodeInfo is null"}
        val uniquenessDmlConnectionId = virtualNodeInfo.uniquenessDmlConnectionId
        requireNotNull(uniquenessDmlConnectionId) {"uniquenessDmlConnectionId is null"}

        val entityManagerFactory = dbConnectionManager.getOrCreateEntityManagerFactory(
            uniquenessDmlConnectionId,
            entitiesSet = jpaEntitiesRegistry.get(CordaDb.Uniqueness.persistenceUnitName)
                ?: throw IllegalStateException(
                    "persistenceUnitName " +
                            "${CordaDb.Uniqueness.persistenceUnitName} is not registered."
                )
        )

        val entityManager = entityManagerFactory.createEntityManager()
        // Enable Hibernate JDBC batch and set the batch size on a per-session basis.
        entityManager.unwrap(Session::class.java).jdbcBatchSize = HIBERNATE_JDBC_BATCH_SIZE

        @Suppress("TooGenericExceptionCaught")
        try {
            block(SessionImpl(holdingIdentity, entityManager))
            entityManager.close()
        } catch (e: Exception) {
            // TODO: Need to figure out what exceptions can be thrown when using JPA directly
            // instead of Hibernate and how to handle
            entityManager.close()
            throw e
        } finally {
            CordaMetrics.Metric.UniquenessBackingStoreSessionExecutionTime
                .builder()
                .withTag(CordaMetrics.Tag.SourceVirtualNode, holdingIdentity.shortHash.toString())
                .build()
                .record(Duration.ofNanos(System.nanoTime() - sessionStartTime))
        }
    }

    protected open inner class SessionImpl(
        private val holdingIdentity: HoldingIdentity,
        private val entityManager: EntityManager
    ) : BackingStore.Session {

        protected open val transactionOps = TransactionOpsImpl()
        private val hibernateSession = entityManager.unwrap(Session::class.java)

        @Suppress("NestedBlockDepth")
        override fun executeTransaction(
            block: (BackingStore.Session, BackingStore.Session.TransactionOps) -> Unit
        ) {
            val transactionStartTime = System.nanoTime()

            try {
                for (attemptNumber in 1..MAX_ATTEMPTS) {
                    try {
                        entityManager.transaction.begin()
                        block(this, transactionOps)
                        entityManager.transaction.commit()

                        CordaMetrics.Metric.UniquenessBackingStoreTransactionAttempts
                            .builder()
                            .withTag(CordaMetrics.Tag.SourceVirtualNode, holdingIdentity.shortHash.toString())
                            .build()
                            .record(attemptNumber.toDouble())

                        return
                    } catch (e: Exception) {
                        when (e) {
                            is EntityExistsException,
                            is RollbackException,
                            is OptimisticLockException -> {
                                // [EntityExistsException] Occurs when another worker committed a
                                // request with conflicting input states. Retry (by not re-throwing the
                                // exception), because the requests with conflicts are removed from the
                                // batch by the code passed in as `block`.

                                // TODO This is needed because some of the exceptions
                                //  we retry do not roll the transaction back. Once
                                //  we improve our error handling in CORE-4983 this
                                //  won't be necessary
                                if (entityManager.transaction.isActive) {
                                    entityManager.transaction.rollback()
                                    log.debug { "Rolled back transaction" }
                                }

                                CordaMetrics.Metric.UniquenessBackingStoreTransactionErrorCount
                                    .builder()
                                    .withTag(CordaMetrics.Tag.SourceVirtualNode, holdingIdentity.shortHash.toString())
                                    .withTag(CordaMetrics.Tag.ErrorType, e.javaClass.simpleName)
                                    .build()
                                    .increment()

                                if (attemptNumber < MAX_ATTEMPTS) {
                                    log.warn(
                                        "Retrying DB operation. The request might have been " +
                                                "handled by a different notary worker or a DB error " +
                                                "occurred when attempting to commit.",
                                        e
                                    )
                                } else {
                                    throw IllegalStateException(
                                        "Failed to execute transaction after the maximum number of " +
                                                "attempts ($MAX_ATTEMPTS).",
                                        e
                                    )
                                }
                            }

                            else -> {
                                // TODO: Revisit handled exceptions, this is a subset of what
                                // we handled in C4
                                log.warn("Unexpected error occurred", e)
                                // We potentially leak a database connection, if we don't rollback. When
                                // the HSM signing operation throws an exception this code path is
                                // triggered.
                                if (entityManager.transaction.isActive) {
                                    entityManager.transaction.rollback()
                                    log.debug { "Rolled back transaction" }
                                }

                                CordaMetrics.Metric.UniquenessBackingStoreTransactionErrorCount
                                    .builder()
                                    .withTag(CordaMetrics.Tag.SourceVirtualNode, holdingIdentity.shortHash.toString())
                                    .build()
                                    .increment()

                                throw e
                            }
                        }
                    }
                }
            } finally {
                CordaMetrics.Metric.UniquenessBackingStoreTransactionExecutionTime
                    .builder()
                    .withTag(CordaMetrics.Tag.SourceVirtualNode, holdingIdentity.shortHash.toString())
                    .build()
                    .record(Duration.ofNanos(System.nanoTime() - transactionStartTime))
            }
        }

        override fun getStateDetails(
            states: Collection<UniquenessCheckStateRef>
        ): Map<UniquenessCheckStateRef, UniquenessCheckStateDetails> {

            val queryStartTime = System.nanoTime()

            val results = HashMap<
                    UniquenessCheckStateRef, UniquenessCheckStateDetails>()

            val statePks = states.map{
                UniquenessTxAlgoStateRefKey(it.txHash.algorithm, it.txHash.bytes, it.stateIndex)
            }

            // Use Hibernate Session to fetch multiple state entities by their primary keys.
            val multiLoadAccess =
                hibernateSession.byMultipleIds(UniquenessStateDetailEntity::class.java)

            // multiLoad will return [null] for each ID that was not found in the DB.
            // However, we don't want to keep those.
            val existing = multiLoadAccess.multiLoad(statePks).filterNotNull()

            existing.forEach { stateEntity ->
                val consumingTxId =
                    if (stateEntity.consumingTxId != null) {
                        SecureHashImpl(stateEntity.consumingTxIdAlgo!!, stateEntity.consumingTxId!!)
                    } else null
                val returnedState = UniquenessCheckStateRefImpl(
                    SecureHashImpl(stateEntity.issueTxIdAlgo, stateEntity.issueTxId),
                    stateEntity.issueTxOutputIndex)
                results[returnedState] = UniquenessCheckStateDetailsImpl(returnedState, consumingTxId)
            }

            CordaMetrics.Metric.UniquenessBackingStoreDbReadTime
                .builder()
                .withTag(CordaMetrics.Tag.SourceVirtualNode, holdingIdentity.shortHash.toString())
                .withTag(CordaMetrics.Tag.OperationName, "getStateDetails")
                .build()
                .record(Duration.ofNanos(System.nanoTime() - queryStartTime))

            return results
        }

        override fun getTransactionDetails(
            txIds: Collection<SecureHash>
        ): Map<out SecureHash, UniquenessCheckTransactionDetailsInternal> {

            val queryStartTime = System.nanoTime()

            val txPks = txIds.map {
                UniquenessTxAlgoIdKey(it.algorithm, it.bytes)
            }

            // Use Hibernate Session to fetch multiple transaction entities by their primary keys.
            val multiLoadAccess =
                hibernateSession.byMultipleIds(UniquenessTransactionDetailEntity::class.java)

            // multiLoad will return [null] for each ID that was not found in the DB.
            // However, we don't want to keep those.
            val existing = multiLoadAccess.multiLoad(txPks).filterNotNull()

            val results = existing.map { txEntity ->
                val result = when (txEntity.result) {
                    RESULT_ACCEPTED_REPRESENTATION -> {
                        UniquenessCheckResultSuccessImpl(txEntity.commitTimestamp)
                    }
                    RESULT_REJECTED_REPRESENTATION -> {
                        // If the transaction is rejected we need to make sure it is also
                        // stored in the rejected tx table
                        UniquenessCheckResultFailureImpl(
                            txEntity.commitTimestamp,
                            getTransactionError(txEntity) ?: throw IllegalStateException(
                                "Transaction with id ${txEntity.txId} was rejected but no records were " +
                                        "found in the rejected transactions table"
                            )
                        )
                    }
                    else -> throw IllegalStateException(
                        "Transaction result can only be " +
                                "'$RESULT_ACCEPTED_REPRESENTATION' or '$RESULT_REJECTED_REPRESENTATION'"
                    )
                }
                val txHash = SecureHashImpl(txEntity.txIdAlgo, txEntity.txId)
                txHash to UniquenessCheckTransactionDetailsInternal(txHash, result)
            }.toMap()

            CordaMetrics.Metric.UniquenessBackingStoreDbReadTime
                .builder()
                .withTag(CordaMetrics.Tag.SourceVirtualNode, holdingIdentity.shortHash.toString())
                .withTag(CordaMetrics.Tag.OperationName, "getTransactionDetails")
                .build()
                .record(Duration.ofNanos(System.nanoTime() - queryStartTime))

            return results
        }

        private fun getTransactionError(
            txEntity: UniquenessTransactionDetailEntity
        ): UniquenessCheckError? {

            val queryStartTime = System.nanoTime()

            val existing = entityManager.createNamedQuery(
                "UniquenessRejectedTransactionEntity.select",
                UniquenessRejectedTransactionEntity::class.java
            )
                .setParameter("txAlgo", txEntity.txIdAlgo)
                .setParameter("txId", txEntity.txId)
                .resultList as List<UniquenessRejectedTransactionEntity>

            return existing.firstOrNull()?.let { rejectedTxEntity ->
                jpaBackingStoreObjectMapper().readValue(
                    rejectedTxEntity.errorDetails, UniquenessCheckError::class.java
                )
            }.also {
                CordaMetrics.Metric.UniquenessBackingStoreDbReadTime
                    .builder()
                    .withTag(CordaMetrics.Tag.SourceVirtualNode, holdingIdentity.shortHash.toString())
                    .withTag(CordaMetrics.Tag.OperationName, "getTransactionError")
                    .build()
                    .record(Duration.ofNanos(System.nanoTime() - queryStartTime))
            }
        }

        protected open inner class TransactionOpsImpl : BackingStore.Session.TransactionOps {

            override fun createUnconsumedStates(
                stateRefs: Collection<UniquenessCheckStateRef>
            ) {
                stateRefs.forEach { stateRef ->
                    entityManager.persist(
                        UniquenessStateDetailEntity(
                            stateRef.txHash.algorithm,
                            stateRef.txHash.bytes,
                            stateRef.stateIndex,
                            null, // Unconsumed
                            null // Unconsumed
                        )
                    )
                }
            }

            override fun consumeStates(
                consumingTxId: SecureHash,
                stateRefs: Collection<UniquenessCheckStateRef>
            ) {
                stateRefs.forEach { stateRef ->
                    val safeUpdate = entityManager.createNamedQuery(
                        "UniquenessStateDetailEntity.consumeWithProtection"
                    )
                        .setParameter("consumingTxAlgo", consumingTxId.algorithm)
                        .setParameter("consumingTxId", consumingTxId.bytes)
                        .setParameter("issueTxAlgo", stateRef.txHash.algorithm)
                        .setParameter("issueTxId", stateRef.txHash.bytes)
                        .setParameter("stateIndex", stateRef.stateIndex)

                    val updatedRowCount = safeUpdate.executeUpdate()

                    if (updatedRowCount == 0) {
                        // TODO: Figure out application specific exceptions
                        throw EntityExistsException(
                            "No states were consumed, this might be an in-flight double spend"
                        )
                    }
                }
            }

            override fun commitTransactions(
                transactionDetails: Collection<Pair<
                        UniquenessCheckRequestInternal, UniquenessCheckResult>>
            ) {
                val commitStartTime = System.nanoTime()

                transactionDetails.forEach { (request, result) ->
                    entityManager.persist(
                        UniquenessTransactionDetailEntity(
                            request.txId.algorithm,
                            request.txId.bytes,
                            request.originatorX500Name,
                            request.timeWindowUpperBound,
                            result.resultTimestamp,
                            result.toCharacterRepresentation()
                        )
                    )

                    if (result is UniquenessCheckResultFailure) {
                        entityManager.persist(
                            UniquenessRejectedTransactionEntity(
                                request.txId.algorithm,
                                request.txId.bytes,
                                jpaBackingStoreObjectMapper().writeValueAsBytes(result.error)
                            )
                        )
                    }
                }

                CordaMetrics.Metric.UniquenessBackingStoreDbCommitTime
                    .builder()
                    .withTag(CordaMetrics.Tag.SourceVirtualNode, holdingIdentity.shortHash.toString())
                    .build()
                    .record(Duration.ofNanos(System.nanoTime() - commitStartTime))
            }
        }
    }
}
