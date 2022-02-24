package net.corda.messagebus.db.persistence

import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils.getEntityManagerConfiguration
import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.db.datamodel.CommittedOffsetEntry
import net.corda.messagebus.db.datamodel.TopicEntry
import net.corda.messagebus.db.datamodel.TopicRecordEntry
import net.corda.messagebus.db.datamodel.TransactionRecordEntry
import net.corda.messagebus.db.datamodel.TransactionState
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import net.corda.test.util.LoggingUtils.emphasise
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.from
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.StringWriter
import java.time.Instant
import java.util.*
import javax.persistence.EntityManagerFactory


class DBAccessIntegrationTest {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger("DBWriterTest")

        private val entityManagerFactoryFactory = EntityManagerFactoryFactoryImpl()
        private val lbm = LiquibaseSchemaMigratorImpl()
        private val dbConfig = getEntityManagerConfiguration("test")
        lateinit var emf: EntityManagerFactory

        private const val topic = "topic1"
        private val key = "key".toByteArray()
        private val value = "value".toByteArray()
        private const val consumerGroup = "group1"

        @Suppress("unused")
        @JvmStatic
        @BeforeAll
        fun setupEntities() {
            logger.info("Create Schema for ${dbConfig.dataSource.connection.metaData.url}".emphasise())
            val schemaClass = DbSchema::class.java
            val fullName = schemaClass.packageName + ".messagebus"
            val resourcePrefix = fullName.replace('.', '/')
            val cl = ClassloaderChangeLog(
                linkedSetOf(
                    ClassloaderChangeLog.ChangeLogResourceFiles(
                        "DBWriterTest",
                        listOf("$resourcePrefix/db.changelog-master.xml"),
                        classLoader = schemaClass.classLoader
                    ),
                )
            )
            StringWriter().use {
                lbm.createUpdateSql(dbConfig.dataSource.connection, cl, it)
                logger.info("Schema creation SQL: $it")
            }
            lbm.updateDb(dbConfig.dataSource.connection, cl)

            logger.info("Create Entities".emphasise())

            emf = entityManagerFactoryFactory.create(
                "test",
                listOf(
                    TopicRecordEntry::class.java,
                    CommittedOffsetEntry::class.java,
                    TopicEntry::class.java,
                    TransactionRecordEntry::class.java,
                ),
                dbConfig,
            )

            emf.transaction { entityManager ->
                entityManager.createNativeQuery("CREATE DATABASE test")
            }

            val topicEntry = TopicEntry(topic, 4)

            emf.transaction { em ->
                em.persist(topicEntry)
            }
        }

        @Suppress("unused")
        @AfterAll
        @JvmStatic
        fun done() {
            emf.close()
        }

        fun <T> query(clazz: Class<T>, ql: String): List<T> {
            val em = emf.createEntityManager()
            return try {
                em.createQuery(ql, clazz).resultList
            } finally {
                em.close()
            }
        }

        fun randomId() = UUID.randomUUID().toString()
    }

    @Test
    fun `DBWriter writes topic records`() {
        val timestamp = Instant.parse("2022-01-01T00:00:00.00Z")
        val topic = randomId()
        val transactionRecord = TransactionRecordEntry(randomId())

        val records = listOf(
            TopicRecordEntry(topic, 0, 0, key, value, transactionRecord, timestamp = timestamp),
            TopicRecordEntry(topic, 0, 1, key, value, transactionRecord, timestamp = timestamp),
            TopicRecordEntry(topic, 1, 0, key, value, transactionRecord, timestamp = timestamp),
            TopicRecordEntry(topic, 2, 0, key, value, transactionRecord, timestamp = timestamp),
            TopicRecordEntry(topic, 3, 0, key, value, transactionRecord, timestamp = timestamp),
        )

        val dbAccess = DBAccess(emf)
        dbAccess.writeRecords(records)

        val results = query(
            TopicRecordEntry::class.java,
            "from topic_record where topic in ('$topic') order by partition, record_offset"
        )
        assertThat(results).size().isEqualTo(records.size)
        results.forEachIndexed { index, topicRecordEntry ->
            assertThat(topicRecordEntry).isEqualToComparingFieldByField(records[index])
        }
    }

    @Test
    fun `DBWriter reads topic records, and only up to the limit specified`() {
        val timestamp = Instant.parse("2022-01-01T00:00:00.00Z")
        val topic = randomId()
        val transactionRecord = TransactionRecordEntry(randomId())
        val transactionRecord2 = TransactionRecordEntry(randomId()) // Won't be committed
        val transactionRecord3 = TransactionRecordEntry(randomId())
        val partition5 = listOf(
            TopicRecordEntry(topic, 5, 0, key, value, transactionRecord, timestamp = timestamp),
            TopicRecordEntry(topic, 5, 1, key, value, transactionRecord, timestamp = timestamp),
            TopicRecordEntry(topic, 5, 3, key, value, transactionRecord2, timestamp = timestamp),
            TopicRecordEntry(topic, 5, 4, key, value, transactionRecord2, timestamp = timestamp),
            TopicRecordEntry(topic, 5, 5, key, value, transactionRecord3, timestamp = timestamp),
        )
        val partition6 = listOf(
            TopicRecordEntry(topic, 6, 0, key, value, transactionRecord, timestamp = timestamp),
            TopicRecordEntry(topic, 6, 1, key, value, transactionRecord, timestamp = timestamp),
            TopicRecordEntry(topic, 6, 2, key, value, transactionRecord, timestamp = timestamp),
        )
        val partition7 = listOf(
            TopicRecordEntry(topic, 7, 0, key, value, transactionRecord, timestamp = timestamp),
        )
        val partition8 = listOf(
            TopicRecordEntry(topic, 8, 0, key, value, transactionRecord, timestamp = timestamp),
        )
        val records = partition5 + partition6 + partition7 + partition8

        val dbAccess = DBAccess(emf)
        dbAccess.writeRecords(records)

        dbAccess.readRecords(-1, CordaTopicPartition(topic, 5)).apply {
            assertThat(this).size().isEqualTo(partition5.size)
            forEachIndexed { index, topicRecordEntry ->
                assertThat(topicRecordEntry).isEqualToComparingFieldByField(partition5[index])
            }
        }

        dbAccess.readRecords(-1, CordaTopicPartition(topic, 6), 2).apply {
            assertThat(this).size().isEqualTo(2)
            forEachIndexed { index, topicRecordEntry ->
                assertThat(topicRecordEntry).isEqualToComparingFieldByField(partition6[index])
            }
        }
    }

    @Test
    fun `DBWriter writes transactional records and correctly changes the state`() {
        val transactionId = randomId()
        val transactionRecordEntry = TransactionRecordEntry(transactionId)

        val dbAccess = DBAccess(emf)
        dbAccess.writeTransactionRecord(transactionRecordEntry)

        val nonCommittedResult = query(
            TransactionRecordEntry::class.java,
            "from transaction_record where transactionId = '$transactionId'"
        ).single()
        assertThat(nonCommittedResult.transactionId).isEqualTo(transactionId)
        assertThat(nonCommittedResult.state).isEqualTo(TransactionState.PENDING)

        dbAccess.setTransactionRecordState(transactionRecordEntry.transactionId, TransactionState.COMMITTED)
        val committedResult = query(
            TransactionRecordEntry::class.java,
            "from transaction_record where transactionId = '$transactionId'"
        ).single()
        assertThat(committedResult.transactionId).isEqualTo(transactionId)
        assertThat(committedResult.state).isEqualTo(TransactionState.COMMITTED)
    }

    @Test
    fun `DBWriter writes committed offsets`() {
        val timestamp = Instant.parse("2022-01-01T00:00:00.00Z")
        val dbAccess = DBAccess(emf)

        val topic = randomId()

        val transactionRecord = TransactionRecordEntry(randomId())
        dbAccess.writeTransactionRecord(transactionRecord)

        val offsets = listOf(
            CommittedOffsetEntry(topic, consumerGroup, 0, 0, transactionRecord, timestamp),
            CommittedOffsetEntry(topic, consumerGroup, 0, 1, transactionRecord, timestamp),
            CommittedOffsetEntry(topic, consumerGroup, 0, 2, transactionRecord, timestamp),
            CommittedOffsetEntry(topic, consumerGroup, 1, 0, transactionRecord, timestamp),
            CommittedOffsetEntry(topic, consumerGroup, 1, 5, transactionRecord, timestamp),
        )

        dbAccess.writeOffsets(offsets)

        val results =
            query(CommittedOffsetEntry::class.java, "from topic_consumer_offset order by partition, record_offset")
        assertThat(results).size().isEqualTo(offsets.size)
        results.forEachIndexed { index, topicRecordEntry ->
            assertThat(topicRecordEntry).isEqualToComparingFieldByField(offsets[index])
        }
    }

    @Test
    fun `DBWriter can create new topics and return the correct topics`() {
        val dbAccess = DBAccess(emf)
        val newTopic = randomId()
        dbAccess.createTopic(newTopic, 10)

        val result = query(TopicEntry::class.java, "from topic where topic = '$newTopic'").single()
        assertThat(result.topic).isEqualTo(newTopic)
        assertThat(result.numPartitions).isEqualTo(10)

        val topics = dbAccess.getTopicPartitionMap()
        assertThat(topics.size).isEqualTo(2)
        assertThat(topics[topic]!!).isEqualTo(4)
        assertThat(topics[newTopic]!!).isEqualTo(10)
    }

    @Test
    fun `getMaxOffsetsPerTopic returns the correct map`() {
        val timestamp = Instant.parse("2022-01-01T00:00:00.00Z")
        val dbAccess = DBAccess(emf)

        val topic = randomId()
        val topic2 = randomId()

        val transactionRecord = TransactionRecordEntry(randomId())

        val records = listOf(
            TopicRecordEntry(topic, 0, 0, key, value, transactionRecord, timestamp = timestamp),
            TopicRecordEntry(topic, 0, 1, key, value, transactionRecord, timestamp = timestamp),
            TopicRecordEntry(topic, 1, 0, key, value, transactionRecord, timestamp = timestamp),
            TopicRecordEntry(topic, 1, 1, key, value, transactionRecord, timestamp = timestamp),
            TopicRecordEntry(topic, 1, 2, key, value, transactionRecord, timestamp = timestamp),
            TopicRecordEntry(topic, 1, 3, key, value, transactionRecord, timestamp = timestamp),
            TopicRecordEntry(topic, 2, 0, key, value, transactionRecord, timestamp = timestamp),
            TopicRecordEntry(topic, 2, 7, key, value, transactionRecord, timestamp = timestamp),
            TopicRecordEntry(topic, 3, 0, key, value, transactionRecord, timestamp = timestamp),
            TopicRecordEntry(topic2, 0, 0, key, value, transactionRecord, timestamp = timestamp),
            TopicRecordEntry(topic2, 1, 1, key, value, transactionRecord, timestamp = timestamp),
            TopicRecordEntry(topic2, 2, 0, key, value, transactionRecord, timestamp = timestamp),
            TopicRecordEntry(topic2, 3, 1, key, value, transactionRecord, timestamp = timestamp),
            TopicRecordEntry(topic2, 4, 2, key, value, transactionRecord, timestamp = timestamp),
        )

        dbAccess.writeRecords(records)

        val expectedResult = mapOf(
            CordaTopicPartition(topic, 0) to 1L,
            CordaTopicPartition(topic, 1) to 3L,
            CordaTopicPartition(topic, 2) to 7L,
            CordaTopicPartition(topic, 3) to 0L,
            CordaTopicPartition(topic2, 0) to 0L,
            CordaTopicPartition(topic2, 1) to 1L,
            CordaTopicPartition(topic2, 2) to 0L,
            CordaTopicPartition(topic2, 3) to 1L,
            CordaTopicPartition(topic2, 4) to 2L,
        )

        assertThat(dbAccess).returns(expectedResult, from(DBAccess::getMaxOffsetsPerTopicPartition))
    }

    @Test
    fun `DbAccess returns correct max and min committed offsets`() {
        val timestamp = Instant.parse("2022-01-01T00:00:00.00Z")
        val dbAccess = DBAccess(emf)

        val topic = randomId()
        val group1 = randomId()
        val group2 = randomId()

        val partition0 = CordaTopicPartition(topic, 0)
        val partition1 = CordaTopicPartition(topic, 1)

        val transactionRecord = TransactionRecordEntry(randomId(), TransactionState.COMMITTED)
        val transactionRecord2 = TransactionRecordEntry(randomId())
        dbAccess.writeTransactionRecord(transactionRecord)
        dbAccess.writeTransactionRecord(transactionRecord2)

        val offsets = listOf(
            CommittedOffsetEntry(topic, group1, 0, 2, transactionRecord, timestamp = timestamp),
            CommittedOffsetEntry(topic, group1, 0, 3, transactionRecord, timestamp = timestamp),
            CommittedOffsetEntry(topic, group1, 1, 1, transactionRecord, timestamp = timestamp),
            CommittedOffsetEntry(topic, group1, 1, 3, transactionRecord, timestamp = timestamp),
            CommittedOffsetEntry(topic, group1, 1, 5, transactionRecord2, timestamp = timestamp),

            CommittedOffsetEntry(topic, group2, 0, 6, transactionRecord, timestamp = timestamp),
            CommittedOffsetEntry(topic, group2, 1, 10, transactionRecord, timestamp = timestamp),
        )

        dbAccess.writeOffsets(offsets)

        val minOffsets = dbAccess.getMinCommittedOffsets(group1, setOf(partition0, partition1))
        assertThat(minOffsets.size).isEqualTo(2)
        assertThat(minOffsets[partition0]).isEqualTo(2)
        assertThat(minOffsets[partition1]).isEqualTo(1)

        val maxOffsets = dbAccess.getMaxCommittedOffsets(group1, setOf(partition0, partition1))
        assertThat(maxOffsets.size).isEqualTo(2)
        assertThat(maxOffsets[partition0]).isEqualTo(3)
        assertThat(maxOffsets[partition1]).isEqualTo(3)
    }
}
