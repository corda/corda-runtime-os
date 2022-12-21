package net.corda.uniqueness.backingstore.impl

import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.connection.manager.VirtualNodeDbType
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import net.corda.uniqueness.backingstore.BackingStore
import net.corda.uniqueness.backingstore.jpa.datamodel.JPABackingStoreEntities
import net.corda.uniqueness.backingstore.jpa.datamodel.UniquenessRejectedTransactionEntity
import net.corda.uniqueness.backingstore.jpa.datamodel.UniquenessStateDetailEntity
import net.corda.uniqueness.backingstore.jpa.datamodel.UniquenessTransactionDetailEntity
import net.corda.uniqueness.backingstore.jpa.datamodel.UniquenessTxAlgoIdKey
import net.corda.uniqueness.backingstore.jpa.datamodel.UniquenessTxAlgoStateRefKey
import net.corda.uniqueness.datamodel.common.UniquenessConstants.HIBERNATE_JDBC_BATCH_SIZE
import net.corda.uniqueness.datamodel.common.UniquenessConstants.RESULT_ACCEPTED_REPRESENTATION
import net.corda.uniqueness.datamodel.common.UniquenessConstants.RESULT_REJECTED_REPRESENTATION
import net.corda.uniqueness.datamodel.common.toCharacterRepresentation
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultFailureImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckResultSuccessImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckStateDetailsImpl
import net.corda.uniqueness.datamodel.internal.UniquenessCheckRequestInternal
import net.corda.uniqueness.datamodel.internal.UniquenessCheckTransactionDetailsInternal
import net.corda.utilities.VisibleForTesting
import net.corda.v5.application.uniqueness.model.UniquenessCheckError
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultFailure
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.uniqueness.data.UniquenessCheckStateDetails
import net.corda.virtualnode.HoldingIdentity
import org.hibernate.Session
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import javax.persistence.EntityExistsException
import javax.persistence.EntityManager
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
        const val MAX_ATTEMPTS = 10
    }

    private val lifecycleCoordinator: LifecycleCoordinator = coordinatorFactory
        .createCoordinator<BackingStore>(::eventHandler)

    private val dependentComponents = DependentComponents.of(
        ::dbConnectionManager
    )

    private lateinit var jpaEntities: JpaEntitiesSet

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

    override fun session(holdingIdentity: HoldingIdentity, block: (BackingStore.Session) -> Unit) {
        val entityManagerFactory = dbConnectionManager.getOrCreateEntityManagerFactory(
            VirtualNodeDbType.UNIQUENESS.getSchemaName(holdingIdentity.shortHash),
            DbPrivilege.DML,
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
        log.info("Backing store starting")
        lifecycleCoordinator.start()
    }

    override fun stop() {
        log.info("Backing store stopping")
        lifecycleCoordinator.stop()
    }

    protected open inner class SessionImpl(
        private val entityManager: EntityManager
    ) : BackingStore.Session {

        protected open val transactionOps = TransactionOpsImpl()
        private val hibernateSession = entityManager.unwrap(Session::class.java)

        @Suppress("NestedBlockDepth")
        override fun executeTransaction(
            block: (BackingStore.Session, BackingStore.Session.TransactionOps) -> Unit
        ) {
            for (attemptNumber in 1..MAX_ATTEMPTS) {
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

                            // TODO This is needed because some of the exceptions
                            //  we retry do not roll the transaction back. Once
                            //  we improve our error handling in CORE-4983 this
                            //  won't be necessary
                            if (entityManager.transaction.isActive) {
                                entityManager.transaction.rollback()
                                log.debug { "Rolled back transaction" }
                            }

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
                            throw e
                        }
                    }
                }
            }
        }

        override fun getStateDetails(
            states: Collection<StateRef>
        ): Map<StateRef, UniquenessCheckStateDetails> {

            val results = HashMap<StateRef, UniquenessCheckStateDetails>()

            val statePks = states.map{
                UniquenessTxAlgoStateRefKey(it.transactionHash.algorithm, it.transactionHash.bytes, it.index)
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
                        SecureHash(stateEntity.consumingTxIdAlgo!!, stateEntity.consumingTxId!!)
                    } else null
                val returnedState = StateRef(
                    SecureHash(stateEntity.issueTxIdAlgo, stateEntity.issueTxId),
                    stateEntity.issueTxOutputIndex)
                results[returnedState] = UniquenessCheckStateDetailsImpl(returnedState, consumingTxId)
            }

            return results
        }

        override fun getTransactionDetails(
            txIds: Collection<SecureHash>
        ): Map<SecureHash, UniquenessCheckTransactionDetailsInternal> {

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
                val txHash = SecureHash(txEntity.txIdAlgo, txEntity.txId)
                txHash to UniquenessCheckTransactionDetailsInternal(txHash, result)
            }.toMap()

            return results
        }

        private fun getTransactionError(
            txEntity: UniquenessTransactionDetailEntity
        ): UniquenessCheckError? {

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
            }
        }

        protected open inner class TransactionOpsImpl : BackingStore.Session.TransactionOps {

            override fun createUnconsumedStates(
                stateRefs: Collection<StateRef>
            ) {
                stateRefs.forEach { stateRef ->
                    entityManager.persist(
                        UniquenessStateDetailEntity(
                            stateRef.transactionHash.algorithm,
                            stateRef.transactionHash.bytes,
                            stateRef.index,
                            null, // Unconsumed
                            null // Unconsumed
                        )
                    )
                }
            }

            override fun consumeStates(
                consumingTxId: SecureHash,
                stateRefs: Collection<StateRef>
            ) {
                stateRefs.forEach { stateRef ->
                    val safeUpdate = entityManager.createNamedQuery(
                        "UniquenessStateDetailEntity.consumeWithProtection"
                    )
                        .setParameter("consumingTxAlgo", consumingTxId.algorithm)
                        .setParameter("consumingTxId", consumingTxId.bytes)
                        .setParameter("issueTxAlgo", stateRef.transactionHash.algorithm)
                        .setParameter("issueTxId", stateRef.transactionHash.bytes)
                        .setParameter("stateIndex", stateRef.index)

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
                transactionDetails.forEach { (request, result) ->
                    entityManager.persist(
                        UniquenessTransactionDetailEntity(
                            request.txId.algorithm,
                            request.txId.bytes,
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
            }
        }
    }

    @VisibleForTesting
    fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        log.info("Backing store received event $event")
        when (event) {
            is StartEvent -> {
                dependentComponents.registerAndStartAll(coordinator)
            }
            is StopEvent -> {
                dependentComponents.stopAll()
            }
            is RegistrationStatusChangeEvent -> {
                jpaEntitiesRegistry.register(
                    CordaDb.Uniqueness.persistenceUnitName,
                    JPABackingStoreEntities.classes
                )

                jpaEntities = jpaEntitiesRegistry.get(CordaDb.Uniqueness.persistenceUnitName)
                    ?: throw IllegalStateException(
                        "persistenceUnitName " +
                                "${CordaDb.Uniqueness.persistenceUnitName} is not registered."
                    )

                log.info("Backing store is ${event.status}")
                coordinator.updateStatus(event.status)
            }
            else -> {
                log.warn("Unexpected event ${event}, ignoring")
            }
        }
    }
}
