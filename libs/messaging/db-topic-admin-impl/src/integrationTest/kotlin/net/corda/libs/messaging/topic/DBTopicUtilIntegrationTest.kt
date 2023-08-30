package net.corda.libs.messaging.topic

import com.typesafe.config.ConfigFactory
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils.getEntityManagerConfiguration
import net.corda.messagebus.db.datamodel.CommittedPositionEntry
import net.corda.messagebus.db.datamodel.TopicEntry
import net.corda.messagebus.db.datamodel.TopicRecordEntry
import net.corda.messagebus.db.datamodel.TransactionRecordEntry
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.StringWriter
import java.util.UUID
import javax.persistence.EntityManagerFactory


class DBTopicUtilIntegrationTest {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        private val entityManagerFactoryFactory = EntityManagerFactoryFactoryImpl()
        private val lbm = LiquibaseSchemaMigratorImpl()
        private val dbConfig = getEntityManagerConfiguration(randomId())
        lateinit var emf: EntityManagerFactory

        @Suppress("unused")
        @JvmStatic
        @BeforeAll
        fun setupEntities() {
            logger.info("Create Schema for ${dbConfig.dataSource.connection.metaData.url}")
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

            logger.info("Create Entities")

            emf = entityManagerFactoryFactory.create(
                "test",
                listOf(
                    TopicRecordEntry::class.java,
                    CommittedPositionEntry::class.java,
                    TopicEntry::class.java,
                    TransactionRecordEntry::class.java,
                ),
                dbConfig,
            )
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
    fun `DB Util correctly creates topics`() {
        val newTopic = randomId()
        val conf = ConfigFactory.parseString("""
            topics = [ 
                { 
                    topicName = "$newTopic" 
                    numPartitions = 2 
                } 
            ]
            """
        )
        val dbUtils = DBTopicUtils(emf)
        dbUtils.createTopics(conf)

        val result = query(TopicEntry::class.java, "from topic where topic = '$newTopic'").single()
        Assertions.assertThat(result.topic).isEqualTo(newTopic)
        Assertions.assertThat(result.numPartitions).isEqualTo(2)
    }

    @Test
    fun `DB Util doesnt error when topic added twice`() {
        val newTopic = randomId()
        val conf = ConfigFactory.parseString("""
            topics = [ 
                { 
                    topicName = "$newTopic" 
                    numPartitions = 2 
                } 
            ]
            """
        )
        val dbUtils = DBTopicUtils(emf)
        dbUtils.createTopics(conf)
        dbUtils.createTopics(conf)

        val result = query(TopicEntry::class.java, "from topic where topic = '$newTopic'").single()
        Assertions.assertThat(result.topic).isEqualTo(newTopic)
        Assertions.assertThat(result.numPartitions).isEqualTo(2)
    }
}
