package repository

import net.corda.crypto.persistence.db.model.CryptoEntities
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.orm.EntityManagerConfiguration
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import org.junit.jupiter.api.AfterAll
import javax.persistence.EntityManagerFactory

abstract class CryptoRepositoryTest {
    private val dbs: List<Pair<EntityManagerConfiguration, EntityManagerFactory>>

    private companion object {
        private const val MIGRATION_FILE_LOCATION = "net/corda/db/schema/crypto/db.changelog-master.xml"
        private const val MIGRATION_FILE_LOCATION_VNODE = "net/corda/db/schema/vnode-crypto/db.changelog-master.xml"
    }

    /**
     * Creates an in-memory database, applies the relevant migration scripts, and initialises
     * the entityManagerFactories.
     */
    init {
        dbs = mapOf(
            "cluster" to MIGRATION_FILE_LOCATION,
            "vnode" to MIGRATION_FILE_LOCATION_VNODE,
            )
            .map { (k,v) ->
                val dbChange = ClassloaderChangeLog(
                    linkedSetOf(
                        ClassloaderChangeLog.ChangeLogResourceFiles(
                            DbSchema::class.java.packageName,
                            listOf(v),
                            DbSchema::class.java.classLoader
                        )
                    )
                )
                val dbConfig =
                    DbUtils.getEntityManagerConfiguration(
                        inMemoryDbName = "${this::class.java.simpleName}-$k",
                        schemaName = k,
                        createSchema = true
                    )
                dbConfig.dataSource.connection.use { connection ->
                    LiquibaseSchemaMigratorImpl().updateDb(connection, dbChange)
                }
                val entityManagerFactory = EntityManagerFactoryFactoryImpl().create(
                    this::class.java.simpleName,
                    CryptoEntities.classes.toList(),
                    dbConfig
                )

                Pair(dbConfig, entityManagerFactory)
            }
    }

    @Suppress("Unused")
    @AfterAll
    fun cleanup() {
        dbs.forEach {
            it.first.close()
            it.second.close()
        }
    }

    @Suppress("Unused")
    private fun emfs(): List<EntityManagerFactory> = dbs.map { it.second }
}
