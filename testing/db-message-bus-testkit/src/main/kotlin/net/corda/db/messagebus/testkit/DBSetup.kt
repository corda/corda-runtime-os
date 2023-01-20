package net.corda.db.messagebus.testkit

import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.TestInMemoryEntityManagerConfiguration
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.osgi.framework.BundleContext
import org.osgi.framework.FrameworkUtil
import java.io.StringWriter

object DBSetup: BeforeAllCallback {

    var isDB = false
        private set

    private fun BundleContext.isDBBundle() =
        bundles.find { it.symbolicName.contains("db-message-bus-impl") } != null

    override fun beforeAll(context: ExtensionContext?) {
        FrameworkUtil.getBundle(this::class.java)?.also { bundle ->
            isDB = bundle.bundleContext.isDBBundle()
            if (isDB) {
                setupEntities()
            }
        }
    }

    private fun setupEntities() {
        val dbConfig = TestInMemoryEntityManagerConfiguration(DbSchema.DB_MESSAGE_BUS)

        val schemaClass = DbSchema::class.java
        val fullName = schemaClass.packageName + ".messagebus"
        val resourcePrefix = fullName.replace('.', '/')
        val cl = ClassloaderChangeLog(
            linkedSetOf(
                ClassloaderChangeLog.ChangeLogResourceFiles(
                    "DBTestkit",
                    listOf("$resourcePrefix/db.changelog-master.xml"),
                    classLoader = schemaClass.classLoader
                ),
            )
        )
        val lbm = LiquibaseSchemaMigratorImpl()
        StringWriter().use {
            lbm.createUpdateSql(dbConfig.dataSource.connection, cl, it)
        }
        lbm.updateDb(dbConfig.dataSource.connection, cl)
    }
}
