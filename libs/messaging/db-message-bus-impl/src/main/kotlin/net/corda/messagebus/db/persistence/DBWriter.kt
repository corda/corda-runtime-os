package net.corda.messagebus.db.persistence

import net.corda.orm.utils.transaction
import net.corda.v5.base.annotations.VisibleForTesting
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

/**
 */
class DBWriter(
    private val entityManagerFactory: EntityManagerFactory,
) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    fun getMaxCommittedOffset(topic: String, consumerGroup: String, partitions: Set<Int>): Map<Int, Long?> {
        if (partitions.isEmpty()) {
            return emptyMap()
        }

        val maxOffsets: MutableMap<Int, Long?> = partitions.associateWith { null }.toMutableMap()
        executeWithErrorHandling("retrieve max committed offsets for consumer group $consumerGroup on topic $topic and partitions $partitions") {
//            val partitionsList = MutableList(partitions.size) { "?" }.joinToString(", ", "(", ")")
//            val sqlStatement = maxCommittedOffsetsStmt.replace("[partitions_list]", partitionsList)
//            val stmt = it.prepareStatement(sqlStatement)
//            stmt.setString(1, topic)
//            stmt.setString(2, consumerGroup)
//            partitions.forEachIndexed { index, partition -> stmt.setInt(3 + index, partition) }
//
//            val result = stmt.executeQuery()
//            while (result.next()) {
//                val partition = result.getInt(1)
//                val maxOffset = result.getLong(2)
//                if (!result.wasNull()) {
//                    maxOffsets[partition] = maxOffset
//                }
//            }
        }

        return maxOffsets
    }

    fun getMaxOffsetsPerTopic(): Map<String, Map<Int, Long?>> {
        val maxOffsetsPerTopic = mutableMapOf<String, MutableMap<Int, Long?>>()

        executeWithErrorHandling("retrieve max offsets per topic") {

            val partitionsPerTopic = getTopicPartitionMap()
            partitionsPerTopic.forEach { (topic, partitions) ->
                maxOffsetsPerTopic[topic] = mutableMapOf()
                (1..partitions).forEach { partition -> maxOffsetsPerTopic[topic]!![partition] = null }
            }

//            val stmt = it.prepareStatement(maxOffsetsStatement)
//            val result = stmt.executeQuery()
//            while (result.next()) {
//                val topic = result.getString(1)
//                val partition = result.getInt(2)
//                val maxOffset = result.getLong(3)
//                maxOffsetsPerTopic[topic]!![partition] = maxOffset
//            }
        }

        return maxOffsetsPerTopic
    }

    fun createTopic(topic: String, partitions: Int) {
        executeWithErrorHandling("create the topic $topic") { em ->
            em.persist(TopicEntry(topic, partitions))
        }
    }

    fun getTopicPartitionMap(): Map<String, Int> {
        val partitionsPerTopic = mutableMapOf<String, Int>()

        executeWithErrorHandling("retrieve all the topics") { em ->
            val builder = em.criteriaBuilder
            val query = builder.createQuery(TopicEntry::class.java)
            val root = query.from(TopicEntry::class.java)
            query.multiselect(root.get<String>("topic"), root.get<Int>("numPartitions"))
            val results = em.createQuery(query).resultList
            partitionsPerTopic.putAll(results.associate { it.topic to it.numPartitions })
        }

        return partitionsPerTopic
    }

    fun deleteRecordsOlderThan(topic: String, timestamp: Instant) {
        executeWithErrorHandling("clean up records older than $timestamp") { em ->
            val builder = em.criteriaBuilder
            val delete = builder.createCriteriaDelete(TopicRecordEntry::class.java)
            val root = delete.from(TopicRecordEntry::class.java)
            delete.where(
                builder.and(
                    builder.equal(
                        root.get<String>("topic"),
                        topic
                    ),
                    builder.lessThan(
                        root.get("timestamp"),
                        timestamp
                    )
                )
            )
            em.createQuery(delete).executeUpdate()
        }
    }

    fun deleteOffsetsOlderThan(topic: String, timestamp: Instant) {
        executeWithErrorHandling("clean up offsets older than $timestamp") { em ->
            val builder = em.criteriaBuilder
            val delete = builder.createCriteriaDelete(CommittedOffsetEntry::class.java)
            val root = delete.from(CommittedOffsetEntry::class.java)
            delete.where(
                builder.and(
                    builder.equal(
                        root.get<String>("topic"),
                        topic
                    ),
                    builder.lessThan(
                        root.get("timestamp"),
                        timestamp
                    )
                )
            )
            em.createQuery(delete).executeUpdate()
        }
    }

    fun writeOffsets(offsets: List<CommittedOffsetEntry>) {
        executeWithErrorHandling("write offsets") { em ->
            offsets.forEach { offset ->
                em.persist(offset)
            }
        }
    }

    fun writeTransactionId(entry: TransactionRecordEntry) {
        executeWithErrorHandling("write records") { entityManager ->
            entityManager.persist(entry)
        }
    }

    fun makeRecordsVisible(transactionId: String) {
        executeWithErrorHandling("commitRecords") { em ->
            val recordTransaction = em.find(TransactionRecordEntry::class.java, transactionId)
            recordTransaction.visible = true
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
     * Executes the specified operation with the necessary error handling.
     * If an error arises during execution, the transaction is rolled back and the exception is re-thrown.
     */
    @VisibleForTesting
    internal fun executeWithErrorHandling(
        operationName: String,
        operation: (emf: EntityManager) -> Unit,
    ) {
        try {
            entityManagerFactory.transaction {
                operation(it)
            }
        } catch (e: Exception) {
            log.error("Error while trying to $operationName. Transaction will be rolled back.", e)
            throw e
        }
    }
}
