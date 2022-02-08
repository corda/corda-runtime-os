package net.corda.messagebus.db.persistence

import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.testkit.DbUtils.getEntityManagerConfiguration
import net.corda.messagebus.db.datamodel.CommittedOffsetEntry
import net.corda.messagebus.db.datamodel.TopicEntry
import net.corda.messagebus.db.datamodel.TopicRecordEntry
import net.corda.messagebus.db.datamodel.TransactionRecordEntry
import net.corda.messagebus.db.datamodel.TransactionState
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import net.corda.test.util.LoggingUtils.emphasise
import org.assertj.core.api.Assertions.assertThat
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
        private const val topic2 = "topic2"
        private const val consumerGroup = "group1"

        @Suppress("unused")
        @JvmStatic
        @BeforeAll
        fun setupEntities() {
            logger.info("Create Schema for ${dbConfig.dataSource.connection.metaData.url}".emphasise())
            val cl = ClassloaderChangeLog(
                linkedSetOf(
                    ClassloaderChangeLog.ChangeLogResourceFiles(
                        "DBWriterTest",
                        listOf("migration/db.changelog-master.xml"),
                        classLoader = DBAccessIntegrationTest::class.java.classLoader
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

        val records = listOf(
            TopicRecordEntry(topic, 0, 0, "key1".toByteArray(), "value1".toByteArray(), "id", timestamp = timestamp),
            TopicRecordEntry(topic, 0, 1, "key2".toByteArray(), "value2".toByteArray(), "id", timestamp = timestamp),
            TopicRecordEntry(topic, 1, 0, "key3".toByteArray(), "value3".toByteArray(), "id", timestamp = timestamp),
            TopicRecordEntry(topic, 2, 0, "key4".toByteArray(), "value4".toByteArray(), "id", timestamp = timestamp),
            TopicRecordEntry(topic, 3, 0, "key5".toByteArray(), "value5".toByteArray(), "id", timestamp = timestamp),
        )

        val dbAccess = DBAccess(emf)
        dbAccess.writeRecords(records)

        val results = query(TopicRecordEntry::class.java, "from topic_record order by partition, record_offset")
        assertThat(results).size().isEqualTo(records.size)
        results.forEachIndexed { index, topicRecordEntry ->
            assertThat(topicRecordEntry).isEqualToComparingFieldByField(records[index])
        }

        val transactionTableResults = query(TransactionRecordEntry::class.java, "from transaction_record")
        assertThat(transactionTableResults).isEmpty()
    }

    @Test
    fun `DBWriter writes transactional records and makes them visible`() {
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

        dbAccess.makeRecordsVisible(transactionRecordEntry.transactionId)
        val committedResult = query(
            TransactionRecordEntry::class.java,
            "from transaction_record where transactionId = '$transactionId'"
        ).single()
        assertThat(committedResult.transactionId).isEqualTo(transactionId)
        assertThat(committedResult.state).isEqualTo(TransactionState.COMMITTED)
    }


    @Test
    fun `DBWriter makes aborted transactional records invisible`() {
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

        dbAccess.makeRecordsInvisible(transactionRecordEntry.transactionId)
        val committedResult = query(
            TransactionRecordEntry::class.java,
            "from transaction_record where transactionId = '$transactionId'"
        ).single()
        assertThat(committedResult.transactionId).isEqualTo(transactionId)
        assertThat(committedResult.state).isEqualTo(TransactionState.ABORTED)
    }

    @Test
    fun `DBWriter writes commited offsets`() {
        val timestamp = Instant.parse("2022-01-01T00:00:00.00Z")

        val offsets = listOf(
            CommittedOffsetEntry(topic, consumerGroup, 0, 0, timestamp),
            CommittedOffsetEntry(topic, consumerGroup, 0, 1, timestamp),
            CommittedOffsetEntry(topic, consumerGroup, 0, 2, timestamp),
            CommittedOffsetEntry(topic, consumerGroup, 1, 0, timestamp),
            CommittedOffsetEntry(topic, consumerGroup, 1, 5, timestamp),
        )

        val dbAccess = DBAccess(emf)
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
        dbAccess.createTopic(topic2, 10)

        val results = query(TopicEntry::class.java, "from topic order by topic")
        assertThat(results.size).isEqualTo(2)
        assertThat(results[1].topic).isEqualTo(topic2)
        assertThat(results[1].numPartitions).isEqualTo(10)

        val topics = dbAccess.getTopicPartitionMap()
        assertThat(topics.size).isEqualTo(2)
        assertThat(topics[topic]!!).isEqualTo(4)
        assertThat(topics[topic2]!!).isEqualTo(10)
    }
}
