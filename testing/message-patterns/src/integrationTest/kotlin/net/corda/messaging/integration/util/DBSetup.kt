package net.corda.messaging.integration.util

import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.messagebus.db.datamodel.CommittedPositionEntry
import net.corda.messagebus.db.datamodel.TopicEntry
import net.corda.messagebus.db.datamodel.TopicRecordEntry
import net.corda.messagebus.db.datamodel.TransactionRecordEntry
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
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
    }

    fun close() {
        emf?.close()
    }
}
