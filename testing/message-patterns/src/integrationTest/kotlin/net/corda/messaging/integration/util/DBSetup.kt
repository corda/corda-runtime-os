package net.corda.messaging.integration.util

import com.typesafe.config.ConfigFactory
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.messagebus.db.datamodel.CommittedPositionEntry
import net.corda.messagebus.db.datamodel.TopicEntry
import net.corda.messagebus.db.datamodel.TopicRecordEntry
import net.corda.messagebus.db.datamodel.TransactionRecordEntry
import net.corda.messaging.kafka.integration.TopicTemplates
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import java.io.StringWriter
import javax.persistence.EntityManagerFactory

object DBSetup {
    private var emf: EntityManagerFactory? = null

    fun setupEntities(persistenceUnitName: String) {
        val entityManagerFactoryFactory = EntityManagerFactoryFactoryImpl()
        val lbm = LiquibaseSchemaMigratorImpl()
        val dbConfig = DbUtils.getEntityManagerConfiguration(DbSchema.DB_MESSAGE_BUS)

        val schemaClass = DbSchema::class.java
        val fullName = schemaClass.packageName + ".messagebus"
        val resourcePrefix = fullName.replace('.', '/')
        val cl = ClassloaderChangeLog(
            linkedSetOf(
                ClassloaderChangeLog.ChangeLogResourceFiles(
                    "PublisherIntegrationTest",
                    listOf("$resourcePrefix/db.changelog-master.xml"),
                    classLoader = schemaClass.classLoader
                ),
            )
        )
        StringWriter().use {
            lbm.createUpdateSql(dbConfig.dataSource.connection, cl, it)
        }
        lbm.updateDb(dbConfig.dataSource.connection, cl)

        emf = entityManagerFactoryFactory.create(
            persistenceUnitName,
            listOf(
                TopicRecordEntry::class.java,
                CommittedPositionEntry::class.java,
                TopicEntry::class.java,
                TransactionRecordEntry::class.java,
            ),
            dbConfig,
        )

        emf!!.transaction { entityManager ->
            entityManager.createNativeQuery("CREATE DATABASE test")
        }

        val configs = listOf(
            TopicTemplates.PUBLISHER_TEST_DURABLE_TOPIC1_TEMPLATE,
            TopicTemplates.PUBLISHER_TEST_DURABLE_TOPIC2_TEMPLATE
        )
            .map {
                val conf = ConfigFactory.parseString(it).getObjectList("topics").first().toConfig()
                TopicEntry(
                    conf.getString("topicName").removePrefix(TopicTemplates.TEST_TOPIC_PREFIX),
                    conf.getInt("numPartitions")
                )
            }

        emf!!.transaction { em ->
            configs.forEach {
                em.persist(it)
            }
        }
    }

    fun close() {
        emf?.close()
    }
}
