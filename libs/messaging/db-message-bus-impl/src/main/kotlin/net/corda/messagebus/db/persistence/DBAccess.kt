package net.corda.messagebus.db.persistence

import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.db.datamodel.CommittedOffsetEntryKey
import net.corda.messagebus.db.datamodel.CommittedPositionEntry
import net.corda.messagebus.db.datamodel.TopicEntry
import net.corda.messagebus.db.datamodel.TopicRecordEntry
import net.corda.messagebus.db.datamodel.TransactionRecordEntry
import net.corda.messagebus.db.datamodel.TransactionState
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.orm.utils.transaction
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.uncheckedCast
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.SQLIntegrityConstraintViolationException
import java.sql.SQLTransientException
import java.time.Instant
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.PersistenceException
import javax.persistence.Tuple

/**
 * Class for DB reads and writes.  Handles the query execution.
 *
 * @param entityManagerFactory Provides the underlying DB connection
 */
@Suppress("TooManyFunctions")
class DBAccess(
    private val entityManagerFactory: EntityManagerFactory,
) {
    // At the moment it's not easy to create partitions, so default value increased to 3 until tooling is available
    // (There are multiple consumers using the same group for some topics and some stay idle if there is only 1 partition)
    private val defaultNumPartitions = 3
    private val autoCreate = true

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
        internal val ATOMIC_TRANSACTION = TransactionRecordEntry("Atomic Transaction", TransactionState.COMMITTED)
    }

    fun close() {
    }

    fun getMaxCommittedPositions(
        groupId: String,
        topicPartitions: Set<CordaTopicPartition>
    ): Map<CordaTopicPartition, Long?> {
        if (topicPartitions.isEmpty()) {
            return emptyMap()
        }

        val returnedPositions = executeWithErrorHandling("get max committed positions") { entityManager ->
            entityManager.createQuery(
                """
                    FROM topic_consumer_offset 
                    WHERE ${CommittedPositionEntry::consumerGroup.name} = '$groupId'
                    ORDER BY ${CommittedPositionEntry::recordPosition.name}
                    """,
                CommittedPositionEntry::class.java
            ).resultList.takeWhile {
                it.transactionId.state == TransactionState.COMMITTED
            }.groupBy {
                CordaTopicPartition(it.topic, it.partition)
            }.filter {
                it.key in topicPartitions
            }.mapValues {
                it.value.maxOf { committedOffsetEntry -> committedOffsetEntry.recordPosition }
            }
        }
        val missingPartitions = topicPartitions - returnedPositions.keys
        return returnedPositions + missingPartitions.associateWith { null }
    }

    fun getMaxOffsetsPerTopicPartition(): Map<CordaTopicPartition, Long> {
        val maxOffsetsPerTopic = mutableMapOf<CordaTopicPartition, Long>()

        executeWithErrorHandling("retrieve max offsets per topic") { entityManager ->
            val builder = entityManager.criteriaBuilder
            val select = builder.createTupleQuery()
            val root = select.from(TopicRecordEntry::class.java)
            select.multiselect(
                root.get<String>(TopicRecordEntry::topic.name),
                root.get<Int>(TopicRecordEntry::partition.name),
                builder.max(root.get<Long>(TopicRecordEntry::recordOffset.name))
            )
            select.groupBy(
                root.get<String>(TopicRecordEntry::topic.name),
                root.get<Int>(TopicRecordEntry::partition.name),
            )
            val results = entityManager.createQuery(select).resultList
            results.forEach {
                val topic = it.get(0, String::class.java)
                val partition: Int = uncheckedCast(it.get(1))
                val offset: Long = uncheckedCast(it.get(2))
                val topicPartition = CordaTopicPartition(topic, partition)
                maxOffsetsPerTopic[topicPartition] = offset
            }
        }

        return maxOffsetsPerTopic
    }

    fun createTopic(topic: String, partitions: Int) {
        executeWithErrorHandling("create the topic") { entityManager ->
            entityManager.persist(TopicEntry(topic, partitions))
        }
    }

    /**
     * If auto topic creation is enabled then will create the topic
     */
    fun getTopicPartitionMapFor(topic: String): Set<CordaTopicPartition> {
        val topicEntry = executeWithErrorHandling(
            "get topic partition map",
            allowDuplicate = true,
        ) { it.merge(TopicEntry(topic, defaultNumPartitions)) }
        val topicPartitions = mutableSetOf<CordaTopicPartition>()
        repeat(topicEntry.numPartitions) { partition ->
            topicPartitions.add(CordaTopicPartition(topic, partition))
        }
        return topicPartitions
    }

    fun getTopicPartitionMap(): Map<String, Int> {
        val partitionsPerTopic = mutableMapOf<String, Int>()

        executeWithErrorHandling("retrieve all the topics") { entityManager ->
            val builder = entityManager.criteriaBuilder
            val query = builder.createQuery(TopicEntry::class.java)
            val root = query.from(TopicEntry::class.java)
            query.multiselect(root.get<String>(TopicEntry::topic.name), root.get<Int>(TopicEntry::numPartitions.name))
            val results = entityManager.createQuery(query).resultList
            partitionsPerTopic.putAll(results.associate { it.topic to it.numPartitions })
        }

        return partitionsPerTopic
    }

    fun deleteRecordsOlderThan(topic: String, timestamp: Instant) {
        executeWithErrorHandling("clean up records older than $timestamp") { entityManager ->
            val builder = entityManager.criteriaBuilder
            val delete = builder.createCriteriaDelete(TopicRecordEntry::class.java)
            val root = delete.from(TopicRecordEntry::class.java)
            delete.where(
                builder.and(
                    builder.equal(
                        root.get<String>(TopicRecordEntry::topic.name),
                        topic
                    ),
                    builder.lessThan(
                        root.get(TopicRecordEntry::timestamp.name),
                        timestamp
                    )
                )
            )
            entityManager.createQuery(delete).executeUpdate()
        }
    }

    fun deleteOffsetsOlderThan(topic: String, timestamp: Instant) {
        executeWithErrorHandling("clean up offsets older than $timestamp") { entityManager ->
            val builder = entityManager.criteriaBuilder
            val delete = builder.createCriteriaDelete(CommittedPositionEntry::class.java)
            val root = delete.from(CommittedPositionEntry::class.java)
            delete.where(
                builder.and(
                    builder.equal(
                        root.get<String>(CommittedPositionEntry::topic.name),
                        topic
                    ),
                    builder.lessThan(
                        root.get(CommittedPositionEntry::timestamp.name),
                        timestamp
                    )
                )
            )
            entityManager.createQuery(delete).executeUpdate()
        }
    }

    fun writeOffsets(offsets: List<CommittedPositionEntry>) {
        executeWithErrorHandling("write offsets") { entityManager ->
            offsets.forEach {
                val key = CommittedOffsetEntryKey(it.topic, it.consumerGroup, it.partition, it.recordPosition)
                if (entityManager.find(CommittedPositionEntry::class.java, key) == null) {
                    entityManager.persist(it)
                }
            }
        }
    }

    /**
     * Special case for writing Atomic Txn Record. We check first if it's in the database as this isn't
     * an error for this one txn record
     */
    fun writeAtomicTransactionRecord() {
        executeWithErrorHandling(
            "write atomic transaction record",
            allowDuplicate = true
        ) { entityManager ->
            if (entityManager.find(TransactionRecordEntry::class.java, ATOMIC_TRANSACTION.transactionId) == null) {
                entityManager.persist(ATOMIC_TRANSACTION)
            }
        }
    }

    fun writeTransactionRecord(entry: TransactionRecordEntry) {
        executeWithErrorHandling("write transaction record $entry") { entityManager ->
            entityManager.persist(entry)
        }
    }

    fun setTransactionRecordState(transactionId: String, state: TransactionState) {
        executeWithErrorHandling("update transaction state with $state") { entityManager ->
            val recordTransaction = entityManager.find(TransactionRecordEntry::class.java, transactionId)
            recordTransaction.state = state
        }

    }

    fun writeRecords(records: List<TopicRecordEntry>) {
        executeWithErrorHandling("write records") { entityManager ->
            records.forEach { record ->
                entityManager.persist(record)
            }
        }
    }

    /**
     * Read records from the given [topicPartition].  Records will be returned which have an offset
     * _greater than_ [fromOffset].
     *
     * @param fromOffset the last read offset (only records with greater offsets will be returned)
     * @param topicPartition the topic partition from which the records will be read
     * @param limit the max number of results to read
     */
    fun readRecords(
        fromOffset: Long,
        topicPartition: CordaTopicPartition,
        limit: Int = Int.MAX_VALUE
    ): List<TopicRecordEntry> {
        return executeWithErrorHandling("read records") { entityManager ->
            entityManager.createQuery(
                """
                    FROM topic_record 
                    WHERE ${TopicRecordEntry::topic.name} = '${topicPartition.topic}'
                    AND ${TopicRecordEntry::partition.name} = ${topicPartition.partition}
                    AND ${TopicRecordEntry::recordOffset.name} >= $fromOffset
                    ORDER BY ${TopicRecordEntry::recordOffset.name}
                    """,
                TopicRecordEntry::class.java
            ).setMaxResults(limit).resultList
        }
    }

    private fun findOffsetToReadUntil(entityManager: EntityManager, topicPartition: CordaTopicPartition): Long {
        return entityManager.createQuery(
                """
                     select t from topic_record t 
                     join transaction_record tr on t.${TopicRecordEntry::transactionId.name} 
                           = tr.${TransactionRecordEntry::transactionId.name}
                     where t.${TopicRecordEntry::topic.name} = '${topicPartition.topic}'
                     and t.${TopicRecordEntry::partition.name} = '${topicPartition.partition}'
                     and tr.${TransactionRecordEntry::state.name} = ${TransactionState.PENDING.ordinal}
                     order by t.${TopicRecordEntry::recordOffset.name}
                    """.trimIndent(),
            TopicRecordEntry::class.java
        ).setMaxResults(1).resultList.firstOrNull()?.recordOffset ?: Long.MAX_VALUE
    }

    fun getLatestRecordOffset(topicPartitions: Collection<CordaTopicPartition>): Map<CordaTopicPartition, Long> {
        return executeWithErrorHandling("read latest offsets") { entityManager ->
            topicPartitions.associateWith {
                entityManager.createQuery(
                    """
                     select t from topic_record t
                     join transaction_record tr on t.${TopicRecordEntry::transactionId.name} 
                           = tr.${TransactionRecordEntry::transactionId.name}
                     where t.${TopicRecordEntry::topic.name} = '${it.topic}'
                     and t.${TopicRecordEntry::partition.name} = '${it.partition}'
                     and tr.${TransactionRecordEntry::state.name} = ${TransactionState.COMMITTED.ordinal}
                     and t.${TopicRecordEntry::recordOffset.name} < ${findOffsetToReadUntil(entityManager, it)}
                     order by t.${TopicRecordEntry::recordOffset.name} desc
                """.trimIndent(),
                    TopicRecordEntry::class.java
                ).setMaxResults(1).resultList.firstOrNull()?.recordOffset ?: 0L
            }
        }
    }

    /**
     * Returns the maximal value of record offset for each [CordaTopicPartition]
     */
    fun getLatestRecordOffsets(): Map<CordaTopicPartition, Long> {
        return executeWithErrorHandling("read latest offsets") { entityManager ->
            entityManager.createQuery(
                """
                 select ${TopicRecordEntry::topic.name}, ${TopicRecordEntry::partition.name}, max(${TopicRecordEntry::recordOffset.name})
                 from topic_record
                 group by ${TopicRecordEntry::topic.name}, ${TopicRecordEntry::partition.name}
                """.trimIndent(),
                Tuple::class.java)
            .resultList
            .associate { r ->
                val topic = r.get(0) as String
                val partition = (r.get(1) as Number).toInt()
                val recordOffset = (r.get(2) as Number).toLong()
                CordaTopicPartition(topic, partition) to recordOffset
            }
        }
    }

    fun getEarliestRecordOffset(topicPartitions: Collection<CordaTopicPartition>): Map<CordaTopicPartition, Long> {
        return executeWithErrorHandling("read earliest offsets") { entityManager ->
            topicPartitions.associateWith {
                entityManager.createQuery(
                    """
                     select t from topic_record t
                     join transaction_record tr on t.${TopicRecordEntry::transactionId.name} 
                           = tr.${TransactionRecordEntry::transactionId.name}
                     where t.${TopicRecordEntry::topic.name} = '${it.topic}'
                     and t.${TopicRecordEntry::partition.name} = '${it.partition}'
                     and tr.${TransactionRecordEntry::state.name} = ${TransactionState.COMMITTED.ordinal}
                     order by t.${TopicRecordEntry::recordOffset.name} 
                """.trimIndent(),
                    TopicRecordEntry::class.java
                ).setMaxResults(1).resultList.firstOrNull()?.recordOffset
                    ?: 0L // This needs to follow auto.offset.reset
            }
        }
    }

    /**
     * Executes the specified operation with the necessary error handling.
     * If an error arises during execution, the transaction is rolled back and the exception is re-thrown.
     */
    private fun <T> executeWithErrorHandling(
        operationName: String,
        allowDuplicate: Boolean = false,
        alreadyTriedOnce: Boolean = false,
        operation: (emf: EntityManager) -> T,
    ): T {
        var result: T? = null
        return try {
            entityManagerFactory.transaction {
                result = operation(it)
                result
            }
        } catch (e: Exception) {
            if (allowDuplicate && e.isDuplicate()) {
                // Someone got here first, not a problem
                // Ideally this would log at debug, but we're leaving it at info because
                // the database will have logged an error and we need some way to alert
                // that it's okay.
                log.info("Attempt at duplicate record is allowed in this instance.")
                result
            } else if (!alreadyTriedOnce && e.isTransientDbException()) {
                // Transient exception may occur when we were stopped on a breakpoint whilst trying to obtain
                // DB connection from a Hikari pool. If we were paused for long enough Hikari will report a condition
                // where connection is not available.
                log.debug { "Transient DB error, let's try one more time: ${e.message}" }
                executeWithErrorHandling(operationName, allowDuplicate, true, operation)
            } else {
                log.error("Error while trying to $operationName. Transaction has been rolled back.", e)
                throw e
            }
        } ?: throw CordaMessageAPIFatalException("Internal error.  DB result should not be null.")
    }

    private fun <T : Exception> Exception.isCausedBy(exceptionType: Class<T>): Boolean {
        var currentCause = this.cause
        while (currentCause != null) {
            if (exceptionType.isAssignableFrom(currentCause::class.java)) {
                return true
            }
            currentCause = currentCause.cause
        }
        return false
    }

    private fun Exception.isDuplicate() =
        isCausedBy(SQLIntegrityConstraintViolationException::class.java) || isCausedBy(PersistenceException::class.java)

    private fun Exception.isTransientDbException() =
        this is SQLTransientException || isCausedBy(SQLTransientException::class.java)
}
