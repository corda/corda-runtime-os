package net.corda.db.messagebus.testkit

import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.osgi.framework.BundleContext
import org.osgi.framework.FrameworkUtil
import java.io.StringWriter

object DBSetup: BeforeAllCallback {
    var isDB = false

    private fun BundleContext.isDBBundle() =
        bundles.find { it.symbolicName.contains("db-message-bus-impl") } != null

    override fun beforeAll(context: ExtensionContext?) {
        val bundleContext = FrameworkUtil.getBundle(this::class.java.classLoader).get().bundleContext
        isDB = bundleContext.isDBBundle()
        if (isDB) {
            setupEntities()
        }
    }

    private fun setupEntities() {
        if (!DbUtils.isInMemory) {
            return
        }

        EntityManagerFactoryFactoryImpl()
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
    }
}
