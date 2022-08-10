package net.corda.uniqueness.backingstore.impl

import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.db.schema.DbSchema
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.orm.JpaEntitiesRegistry
import net.corda.uniqueness.backingstore.BackingStore
import net.corda.uniqueness.backingstore.jpa.datamodel.JPABackingStoreEntities
import net.corda.uniqueness.backingstore.jpa.datamodel.UniquenessRejectedTransactionEntity
import net.corda.uniqueness.backingstore.jpa.datamodel.UniquenessStateDetailEntity
import net.corda.uniqueness.backingstore.jpa.datamodel.UniquenessTransactionDetailEntity
import net.corda.uniqueness.common.datamodel.UniquenessCheckInternalError
import net.corda.uniqueness.common.datamodel.UniquenessCheckInternalRequest
import net.corda.uniqueness.common.datamodel.UniquenessCheckInternalResult
import net.corda.uniqueness.common.datamodel.UniquenessCheckInternalResult.Companion.RESULT_ACCEPTED_REPRESENTATION
import net.corda.uniqueness.common.datamodel.UniquenessCheckInternalResult.Companion.RESULT_REJECTED_REPRESENTATION
import net.corda.uniqueness.common.datamodel.UniquenessCheckInternalStateDetails
import net.corda.uniqueness.common.datamodel.UniquenessCheckInternalStateRef
import net.corda.uniqueness.common.datamodel.UniquenessCheckInternalTransactionDetails
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.SecureHash
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import javax.persistence.EntityExistsException
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.OptimisticLockException
import javax.persistence.RollbackException

@Suppress("ForbiddenComment")
// TODO: Reimplement metrics, config
/**
 * JPA backing store implementation, which uses a JPA compliant database to persist data.
 */
@Component(service = [BackingStore::class])
open class JPABackingStoreImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = JpaEntitiesRegistry::class)
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
    @Reference(service = DbConnectionManager::class)
    private val dbConnectionManager: DbConnectionManager
) : BackingStore {

    private companion object {
        private val log: Logger = contextLogger()

        // TODO: Replace constants with config
        const val DEFAULT_UNIQUENESS_DB_NAME = "uniqueness_default"
        const val MAX_RETRIES = 10
    }

    private val lifecycleCoordinator: LifecycleCoordinator = coordinatorFactory
        .createCoordinator<BackingStore>(::eventHandler)

    private val dependentComponents = DependentComponents.of(
        ::dbConnectionManager
    )

    private lateinit var entityManagerFactory: EntityManagerFactory

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

    override fun session(block: (BackingStore.Session) -> Unit) {
        val entityManager = entityManagerFactory.createEntityManager()

        @Suppress("TooGenericExceptionCaught")
        try {
            block(SessionImpl(entityManager))
            entityManager.close()
        } catch (e: Exception) {
            // TODO: Need to figure out what exceptions can be thrown when using JPA directly
            // instead of Hibernate and how to handle
            entityManager.close()
            throw e
        }
    }

    override fun start() {
        log.info("Uniqueness checker starting.")
        lifecycleCoordinator.start()
    }

    override fun stop() {
        log.info("Uniqueness checker stopping.")
        lifecycleCoordinator.stop()
    }

    override fun close() {
        entityManagerFactory.close()
        stop()
    }

    protected open inner class SessionImpl(
        private val entityManager: EntityManager
    ) : BackingStore.Session {

        protected open val transactionOps = TransactionOpsImpl()

        @Suppress("NestedBlockDepth")
        override fun executeTransaction(
            block: (BackingStore.Session, BackingStore.Session.TransactionOps) -> Unit
        ) {
            for (retryCount in 0..MAX_RETRIES) {
                try {
                    entityManager.transaction.begin()
                    block(this, transactionOps)
                    entityManager.transaction.commit()
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
                            contextLogger().warn(
                                "Retrying DB operation. The request might have been " +
                                    "handled by a different notary worker or a DB error " +
                                    "occurred when attempting to commit.",
                                e
                            )
                            // TODO This is needed because some of the exceptions
                            //  we retry do not roll the transaction back. Once
                            //  we improve our error handling in CORE-4983 this
                            //  won't be necessary
                            if (entityManager.transaction.isActive) {
                                entityManager.transaction.rollback()
                                contextLogger().info("Rolled back transaction.")
                            }
                        }
                        else -> {
                            // TODO: Revisit handled exceptions, this is a subset of what
                            // we handled in C4
                            contextLogger().warn("Unexpected error occurred", e)
                            // We potentially leak a database connection, if we don't rollback. When
                            // the HSM signing operation throws an exception this code path is
                            // triggered.
                            if (entityManager.transaction.isActive) {
                                entityManager.transaction.rollback()
                                contextLogger().info("Rolled back transaction.")
                            }
                            throw e
                        }
                    }
                }
            }
            throw IllegalStateException(
                "Database operation reached the maximum number of " +
                    "retries: $MAX_RETRIES, something went wrong."
            )
        }

        override fun getStateDetails(
            states: Collection<UniquenessCheckInternalStateRef>
        ): Map<UniquenessCheckInternalStateRef, UniquenessCheckInternalStateDetails> {

            val results = HashMap<
                UniquenessCheckInternalStateRef, UniquenessCheckInternalStateDetails>()

            states.forEach { state ->
                val txId = state.txHash
                val stateIndex = state.stateIndex
                val existing = entityManager.createNamedQuery(
                    "UniquenessStateDetailEntity.select",
                    UniquenessStateDetailEntity::class.java
                )
                    .setParameter("txAlgo", txId.algorithm)
                    .setParameter("txId", txId.bytes)
                    .setParameter("stateIndex", stateIndex.toLong())
                    .resultList as List<UniquenessStateDetailEntity>

                existing.firstOrNull()?.let { stateEntity ->
                    val consumingTxId =
                        if (stateEntity.consumingTxId != null) {
                            SecureHash(stateEntity.consumingTxIdAlgo!!, stateEntity.consumingTxId!!)
                        } else null

                    results[state] = UniquenessCheckInternalStateDetails(state, consumingTxId)
                }
            }

            return results
        }

        override fun getTransactionDetails(
            txIds: Collection<SecureHash>
        ): Map<SecureHash, UniquenessCheckInternalTransactionDetails> {

            val results = HashMap<SecureHash, UniquenessCheckInternalTransactionDetails>()

            txIds.forEach { txId ->
                val existing = entityManager.createNamedQuery(
                    "UniquenessTransactionDetailEntity.select",
                    UniquenessTransactionDetailEntity::class.java
                )
                    .setParameter("txAlgo", txId.algorithm)
                    .setParameter("txId", txId.bytes)
                    .resultList as List<UniquenessTransactionDetailEntity>

                existing.firstOrNull()?.let { txEntity ->
                    val result = when (txEntity.result) {
                        RESULT_ACCEPTED_REPRESENTATION -> {
                            UniquenessCheckInternalResult.Success(txEntity.commitTimestamp)
                        }
                        RESULT_REJECTED_REPRESENTATION -> {
                            // If the transaction is rejected we need to make sure it is also
                            // stored in the rejected tx table
                            UniquenessCheckInternalResult.Failure(
                                txEntity.commitTimestamp,
                                getTransactionError(txEntity) ?: throw IllegalStateException(
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

                    results[txId] = UniquenessCheckInternalTransactionDetails(txId, result)
                }
            }

            return results
        }

        private fun getTransactionError(
            txEntity: UniquenessTransactionDetailEntity
        ): UniquenessCheckInternalError? {

            val existing = entityManager.createNamedQuery(
                "UniquenessRejectedTransactionEntity.select",
                UniquenessRejectedTransactionEntity::class.java
            )
                .setParameter("txAlgo", txEntity.txIdAlgo)
                .setParameter("txId", txEntity.txId)
                .resultList as List<UniquenessRejectedTransactionEntity>

            return existing.firstOrNull()?.let { rejectedTxEntity ->
                jpaBackingStoreObjectMapper().readValue(
                    rejectedTxEntity.errorDetails, UniquenessCheckInternalError::class.java
                )
            }
        }

        protected open inner class TransactionOpsImpl : BackingStore.Session.TransactionOps {

            override fun createUnconsumedStates(
                stateRefs: Collection<UniquenessCheckInternalStateRef>
            ) {
                stateRefs.forEach { stateRef ->
                    entityManager.persist(
                        UniquenessStateDetailEntity(
                            stateRef.txHash.algorithm,
                            stateRef.txHash.bytes,
                            stateRef.stateIndex.toLong(),
                            null, // Unconsumed
                            null // Unconsumed
                        )
                    )
                }
            }

            override fun consumeStates(
                consumingTxId: SecureHash,
                stateRefs: Collection<UniquenessCheckInternalStateRef>
            ) {
                stateRefs.forEach { stateRef ->
                    val safeUpdate = entityManager.createNamedQuery(
                        "UniquenessStateDetailEntity.consumeWithProtection"
                    )
                        .setParameter("consumingTxAlgo", consumingTxId.algorithm)
                        .setParameter("consumingTxId", consumingTxId.bytes)
                        .setParameter("issueTxAlgo", stateRef.txHash.algorithm)
                        .setParameter("issueTxId", stateRef.txHash.bytes)
                        .setParameter("stateIndex", stateRef.stateIndex.toLong())

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
                        UniquenessCheckInternalRequest, UniquenessCheckInternalResult>>
            ) {
                transactionDetails.forEach { (request, result) ->
                    entityManager.persist(
                        UniquenessTransactionDetailEntity(
                            request.txId.algorithm,
                            request.txId.bytes,
                            request.timeWindowUpperBound,
                            result.commitTimestamp,
                            result.toCharacterRepresentation()
                        )
                    )

                    if (result is UniquenessCheckInternalResult.Failure) {
                        entityManager.persist(
                            UniquenessRejectedTransactionEntity(
                                request.txId.algorithm,
                                request.txId.bytes,
                                jpaBackingStoreObjectMapper().writeValueAsBytes(result.error)
                            )
                        )
                    }
                }
            }
        }
    }

    @VisibleForTesting
    fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        log.info("Backing store received event $event.")
        when (event) {
            is StartEvent -> {
                dependentComponents.registerAndStartAll(coordinator)
            }
            is StopEvent -> {
                dependentComponents.stopAll()
            }
            is RegistrationStatusChangeEvent -> {
                if (event.status == LifecycleStatus.UP) {

                    createDefaultUniquenessDb()

                    entityManagerFactory = dbConnectionManager.getOrCreateEntityManagerFactory(
                        DEFAULT_UNIQUENESS_DB_NAME,
                        DbPrivilege.DML,
                        entitiesSet = jpaEntitiesRegistry.get(CordaDb.Uniqueness.persistenceUnitName)
                            ?: throw IllegalStateException(
                                "persistenceUnitName " +
                                    "${CordaDb.Uniqueness.persistenceUnitName} is not registered."
                            )
                    )
                }

                log.info("Backing store is ${event.status}")
                coordinator.updateStatus(event.status)
            }
            else -> {
                log.warn("Unexpected event $event!")
            }
        }
    }

    /*
     * FIXME: This is a temporary hack which uses the public schema of the cluster database to
     * store uniqueness data. It needs replacing with a solution to retrieve the appropriate DB
     * connection for a given notary service identity, and a mechanism to create the DB connection
     */
    private fun createDefaultUniquenessDb() {
        jpaEntitiesRegistry.register(
            CordaDb.Uniqueness.persistenceUnitName,
            JPABackingStoreEntities.classes
        )

        val schemaMigrator = LiquibaseSchemaMigratorImpl()
        val changeLog = ClassloaderChangeLog(
            linkedSetOf(
                ClassloaderChangeLog.ChangeLogResourceFiles(
                    DbSchema::class.java.packageName,
                    listOf("net/corda/db/schema/uniqueness/db.changelog-master.xml"),
                    DbSchema::class.java.classLoader
                )
            )
        )

        dbConnectionManager.getClusterDataSource().connection.use { connection ->
            schemaMigrator.updateDb(connection, changeLog)

            dbConnectionManager.putConnection(
                DEFAULT_UNIQUENESS_DB_NAME,
                DbPrivilege.DML,
                dbConnectionManager.clusterConfig,
                "Uniqueness default DB",
                ""
            )
        }
    }
}
