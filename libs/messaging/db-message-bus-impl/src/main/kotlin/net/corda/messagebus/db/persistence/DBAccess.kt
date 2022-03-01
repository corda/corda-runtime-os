package net.corda.messagebus.db.persistence

import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.db.datamodel.CommittedPositionEntry
import net.corda.messagebus.db.datamodel.TopicEntry
import net.corda.messagebus.db.datamodel.TopicRecordEntry
import net.corda.messagebus.db.datamodel.TransactionRecordEntry
import net.corda.messagebus.db.datamodel.TransactionState
import net.corda.orm.utils.transaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

/**
 * Class for DB reads and writes.  Handles the query execution.
 *
 * @param entityManagerFactory Provides the underlying DB connection
 */
@Suppress("TooManyFunctions")
class DBAccess(
    private val entityManagerFactory: EntityManagerFactory,
) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    private fun getCommittedPositions(
        groupId: String,
        topicPartitions: Set<CordaTopicPartition>
    ): Map<CordaTopicPartition, List<CommittedPositionEntry>> {
        return executeWithErrorHandling("get committed positions") { entityManager ->
            entityManager.createQuery(
                """
                    FROM topic_consumer_offset 
                    WHERE ${CommittedPositionEntry::consumerGroup.name} = '$groupId'
                    """,
                CommittedPositionEntry::class.java
            ).resultList.takeWhile {
                it.transactionId.state == TransactionState.COMMITTED
            }.groupBy {
                CordaTopicPartition(it.topic, it.partition)
            }.filter {
                it.key in topicPartitions
            }
        }
    }

    fun getMaxCommittedPositions(
        groupId: String,
        topicPartitions: Set<CordaTopicPartition>
    ): Map<CordaTopicPartition, Long?> {
        if (topicPartitions.isEmpty()) {
            return emptyMap()
        }

        val returnedPositions = getCommittedPositions(groupId, topicPartitions)
            .mapValues {
                it.value.maxOf { committedOffsetEntry -> committedOffsetEntry.recordPosition }
            }
        val missingPartitions = topicPartitions - returnedPositions.keys
        return returnedPositions + missingPartitions.associateWith { null }
    }

    fun getMinCommittedPositions(
        groupId: String,
        topicPartitions: Set<CordaTopicPartition>
    ): Map<CordaTopicPartition, Long> {
        if (topicPartitions.isEmpty()) {
            return emptyMap()
        }

        val returnedPositions = getCommittedPositions(groupId, topicPartitions)
            .mapValues {
                it.value.minOf { committedOffsetEntry -> committedOffsetEntry.recordPosition }
            }
        val missingPartitions = topicPartitions - returnedPositions.keys
        return returnedPositions + missingPartitions.associateWith { 0L }
    }

    fun getMaxOffsetsPerTopicPartition(): Map<CordaTopicPartition, Long> {
        val maxOffsetsPerTopic = mutableMapOf<CordaTopicPartition, Long>()

        executeWithErrorHandling("retrieve max offsets per topic") { entityManager ->
            data class Result(val topic: String, val partition: Int, val offset: Long)

            val builder = entityManager.criteriaBuilder
            val select = builder.createQuery(Result::class.java)
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
                val topicPartition = CordaTopicPartition(it.topic, it.partition)
                maxOffsetsPerTopic[topicPartition] = it.offset
            }
        }

        return maxOffsetsPerTopic
    }

    fun createTopic(topic: String, partitions: Int) {
        executeWithErrorHandling("create the topic") { entityManager ->
            entityManager.persist(TopicEntry(topic, partitions))
        }
    }

    fun getTopicPartitionMapFor(topic: String): TopicEntry {
        return executeWithErrorHandling("retrieve topic partitions") { entityManager ->
            val builder = entityManager.criteriaBuilder
            val query = builder.createQuery(TopicEntry::class.java)
            val root = query.from(TopicEntry::class.java)
            query.multiselect(root.get<String>(TopicEntry::topic.name), root.get<Int>(TopicEntry::numPartitions.name))
            query.where(builder.equal(root.get<String>(TopicEntry::topic.name), topic))
            entityManager.createQuery(query).singleResult
        }
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
            offsets.forEach { offset ->
                entityManager.persist(offset)
            }
        }
    }

    fun writeTransactionRecord(entry: TransactionRecordEntry) {
        executeWithErrorHandling("write transaction records") { entityManager ->
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
                    AND ${TopicRecordEntry::recordOffset.name} > $fromOffset
                    ORDER BY ${TopicRecordEntry::recordOffset.name}
                    """,
                TopicRecordEntry::class.java
            ).setMaxResults(limit).resultList
        }
    }

    private fun findOffsetToReadUntil(topicPartition: CordaTopicPartition): Long {
        return executeWithErrorHandling("read latest offsets") { entityManager ->
            entityManager.createQuery(
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
                     and t.${TopicRecordEntry::recordOffset.name} < ${findOffsetToReadUntil(it)}
                     order by t.${TopicRecordEntry::recordOffset.name} desc
                """.trimIndent(),
                    TopicRecordEntry::class.java
                ).setMaxResults(1).resultList.firstOrNull()?.recordOffset ?: 0L
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
        operation: (emf: EntityManager) -> T,
    ): T {
        return try {
            entityManagerFactory.transaction {
                operation(it)
            }
        } catch (e: Exception) {
            log.error("Error while trying to $operationName. Transaction has been rolled back.", e)
            throw e
        }
    }
}
